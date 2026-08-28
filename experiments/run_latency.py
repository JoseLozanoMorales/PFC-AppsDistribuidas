#!/usr/bin/env python3
"""Compara la misma reconciliación de reserva por TCP y gRPC en TiendaTech."""

import argparse
import csv
import json
import socket
import statistics
import struct
import sys
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERATED = ROOT / "experiments" / "generated"
sys.path.insert(0, str(GENERATED))

import grpc  # noqa: E402
import stock_reservation_pb2 as messages  # noqa: E402
import stock_reservation_pb2_grpc as services  # noqa: E402


def request(cart_id: int, user_id: int, product_id: int, sequence: int) -> dict:
    return {
        "cartId": cart_id,
        "userId": user_id,
        "productId": product_id,
        "quantity": sequence % 2,
        "lamportTimestamp": sequence + 1,
        "deviceId": "latency-experiment",
        "operationId": str(uuid.uuid4()),
    }


def receive_exact(channel: socket.socket, size: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < size:
        chunk = channel.recv(size - len(chunks))
        if not chunk:
            raise ConnectionError("El servidor TCP cerró el canal antes de completar el mensaje")
        chunks.extend(chunk)
    return bytes(chunks)


def run_tcp(host: str, port: int, count: int, ids: tuple[int, int, int]) -> list[tuple]:
    rows = []
    with socket.create_connection((host, port), timeout=5) as channel:
        for sequence in range(count):
            payload = json.dumps(request(*ids, sequence), separators=(",", ":")).encode()
            started = time.perf_counter()
            channel.sendall(struct.pack(">I", len(payload)) + payload)
            length = struct.unpack(">I", receive_exact(channel, 4))[0]
            response = json.loads(receive_exact(channel, length))
            elapsed_ms = (time.perf_counter() - started) * 1000
            rows.append(("TCP", sequence + 1, elapsed_ms, response["accepted"]))
    return rows


def run_grpc(host: str, port: int, count: int, ids: tuple[int, int, int]) -> list[tuple]:
    rows = []
    with grpc.insecure_channel(f"{host}:{port}") as channel:
        stub = services.StockReservationServiceStub(channel)
        for sequence in range(count):
            body = request(*ids, sequence)
            grpc_request = messages.ReservationRequest(
                cart_id=body["cartId"], user_id=body["userId"], product_id=body["productId"],
                quantity=body["quantity"], lamport_timestamp=body["lamportTimestamp"],
                device_id=body["deviceId"], operation_id=body["operationId"])
            started = time.perf_counter()
            response = stub.ReconcileReservation(grpc_request, timeout=5)
            elapsed_ms = (time.perf_counter() - started) * 1000
            rows.append(("gRPC", sequence + 1, elapsed_ms, response.accepted))
    return rows


def percentile95(values: list[float]) -> float:
    ordered = sorted(values)
    position = .95 * (len(ordered) - 1)
    lower = int(position)
    fraction = position - lower
    return ordered[lower] if lower == len(ordered) - 1 else ordered[lower] + fraction * (ordered[lower + 1] - ordered[lower])


def save_csv(path: Path, rows: list[tuple]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    values = [row[2] for row in rows]
    summary = (statistics.mean(values), statistics.median(values),
               statistics.stdev(values), percentile95(values))
    with path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.writer(output)
        writer.writerow(("transport", "sequence", "latency_ms", "accepted",
                         "mean_ms", "median_ms", "stddev_ms", "p95_ms"))
        writer.writerows((transport, sequence, f"{latency:.6f}", str(accepted).lower(),
                          *(f"{value:.6f}" for value in summary))
                         for transport, sequence, latency, accepted in rows)


def report(label: str, rows: list[tuple]) -> None:
    values = [row[2] for row in rows]
    print(f"{label}: n={len(values)}, media={statistics.mean(values):.3f} ms, "
          f"mediana={statistics.median(values):.3f} ms, "
          f"desviación={statistics.stdev(values):.3f} ms, p95={percentile95(values):.3f} ms")


def boxplot(tcp_rows: list[tuple], grpc_rows: list[tuple], path: Path) -> None:
    import matplotlib.pyplot as plt
    path.parent.mkdir(parents=True, exist_ok=True)
    figure, axis = plt.subplots(figsize=(7, 4.5))
    axis.boxplot([[row[2] for row in tcp_rows], [row[2] for row in grpc_rows]],
                 tick_labels=["TCP", "gRPC"], showmeans=True)
    axis.set_title("Latencia de reserva de stock en TiendaTech")
    axis.set_ylabel("Latencia (ms)")
    axis.grid(axis="y", alpha=.25)
    figure.tight_layout()
    figure.savefig(path, dpi=300)
    plt.close(figure)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--tcp-port", type=int, default=9091)
    parser.add_argument("--grpc-port", type=int, default=9092)
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--user-id", type=int, required=True)
    parser.add_argument("--product-id", type=int, required=True)
    parser.add_argument("--tcp-cart-id", type=int, required=True)
    parser.add_argument("--grpc-cart-id", type=int, required=True)
    args = parser.parse_args()
    tcp = run_tcp(args.host, args.tcp_port, args.count,
                  (args.tcp_cart_id, args.user_id, args.product_id))
    grpc_rows = run_grpc(args.host, args.grpc_port, args.count,
                         (args.grpc_cart_id, args.user_id, args.product_id))
    save_csv(ROOT / "experiments/data/latency_sockets.csv", tcp)
    save_csv(ROOT / "experiments/data/latency_grpc.csv", grpc_rows)
    boxplot(tcp, grpc_rows, ROOT / "experiments/figures/latency_boxplot.png")
    report("TCP", tcp)
    report("gRPC", grpc_rows)


if __name__ == "__main__":
    main()
