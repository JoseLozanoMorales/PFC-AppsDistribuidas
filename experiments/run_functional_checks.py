#!/usr/bin/env python3
"""Comprobaciones funcionales TCP/Lamport contra el Inventario integrado."""

import argparse
import json
import socket
import struct
import uuid
from concurrent.futures import ThreadPoolExecutor


def receive_exact(channel: socket.socket, size: int) -> bytes:
    data = bytearray()
    while len(data) < size:
        chunk = channel.recv(size - len(data))
        if not chunk:
            raise ConnectionError("El canal se cerró antes de completar la trama")
        data.extend(chunk)
    return bytes(data)


def exchange(channel: socket.socket, body: dict, fragmented: bool = False) -> dict:
    payload = json.dumps(body, separators=(",", ":")).encode()
    frame = struct.pack(">I", len(payload)) + payload
    if fragmented:
        for byte in frame:
            channel.sendall(bytes((byte,)))
    else:
        channel.sendall(frame)
    length = struct.unpack(">I", receive_exact(channel, 4))[0]
    return json.loads(receive_exact(channel, length))


def command(cart: int, product: int, quantity: int, lamport: int,
            device: str, operation: str | None = None) -> dict:
    return {
        "cartId": cart,
        "userId": 1,
        "productId": product,
        "quantity": quantity,
        "lamportTimestamp": lamport,
        "deviceId": device,
        "operationId": operation or str(uuid.uuid4()),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9091)
    args = parser.parse_args()
    base_cart = 1_000_000_000 + uuid.uuid4().int % 100_000_000
    lamport_cart = base_cart
    stock_carts = (base_cart + 1, base_cart + 2)

    with socket.create_connection((args.host, args.port), timeout=5) as channel:
        operation = str(uuid.uuid4())
        first = exchange(channel, command(lamport_cart, 1, 2, 10, "device-a", operation), True)
        assert first["accepted"] and first["reservedQuantity"] == 2

        replay = exchange(channel, command(lamport_cart, 1, 2, 10, "device-a", operation))
        assert replay["accepted"] and replay["replayed"]

        winner = exchange(channel, command(lamport_cart, 1, 3, 10, "device-z"))
        assert winner["accepted"] and winner["winningDeviceId"] == "device-z"

        stale = exchange(channel, command(lamport_cart, 1, 4, 10, "device-m"))
        assert not stale["accepted"]
        assert stale["reservedQuantity"] == 3 and stale["winningDeviceId"] == "device-z"

    def reserve(cart: int) -> dict:
        with socket.create_connection((args.host, args.port), timeout=5) as channel:
            return exchange(channel, command(cart, 2, 6, 20, f"device-{cart}"))

    with ThreadPoolExecutor(max_workers=2) as pool:
        concurrent = list(pool.map(reserve, stock_carts))
    accepted = sum(bool(result["accepted"]) for result in concurrent)
    assert accepted == 1, f"Se esperaba una reserva aceptada, respuestas={concurrent}"

    print("OK framing fragmentado")
    print("OK canal TCP persistente")
    print("OK idempotencia")
    print("OK orden total Lamport por (reloj, dispositivo)")
    print("OK reserva concurrente sin sobreventa")

    for cart, product in ((lamport_cart, 1), *((cart, 2) for cart in stock_carts)):
        with socket.create_connection((args.host, args.port), timeout=5) as channel:
            cleanup = exchange(channel, command(cart, product, 0, 1_000_000, "cleanup"))
            assert cleanup["accepted"]


if __name__ == "__main__":
    main()
