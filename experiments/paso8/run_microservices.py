#!/usr/bin/env python3
"""Carga complementaria contra el checkout real de TiendaTech a través del Gateway."""

from __future__ import annotations

import argparse
import csv
import json
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


def request(url: str, token: str, method: str = "GET", body: dict | None = None,
            headers: dict | None = None, timeout: float = 30) -> tuple[int, dict, dict]:
    payload = None if body is None else json.dumps(body).encode("utf-8")
    request_headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    if payload is not None:
        request_headers["Content-Type"] = "application/json"
    request_headers.update(headers or {})
    req = urllib.request.Request(url, data=payload, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw) if raw else {}, dict(response.headers)
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"message": raw}
        return error.code, parsed, dict(error.headers)


def warmup(gateway: str, admin_token: str, seconds: float) -> None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        request(f"{gateway}/api/admin/system", admin_token, timeout=10)


def checkout(gateway: str, case: dict, timeout: float) -> dict:
    trace_id = str(case.get("traceId") or uuid.uuid4())
    started = time.perf_counter()
    try:
        status, response, headers = request(
            f"{gateway}/api/ordenes/checkout", str(case["token"]), "POST",
            {"direccionId": int(case["direccionId"]), "metodopagoId": int(case["metodopagoId"])},
            {"X-Trace-Id": trace_id, "Idempotency-Key": str(case.get("idempotencyKey") or trace_id)}, timeout)
        message = str(response.get("message", "")) if isinstance(response, dict) else ""
        return {"case_id": case["caseId"], "trace_id": headers.get("X-Trace-Id", trace_id),
                "http_status": status, "success": 200 <= status < 300,
                "latency_ms": round((time.perf_counter() - started) * 1000, 3), "error": message}
    except Exception as error:  # error de transporte: también es una observación válida
        return {"case_id": case["caseId"], "trace_id": trace_id, "http_status": 0, "success": False,
                "latency_ms": round((time.perf_counter() - started) * 1000, 3),
                "error": f"{type(error).__name__}: {error}"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--gateway", default="http://localhost:8180")
    parser.add_argument("--admin-token", required=True, help="JWT de administrador para preflight y evidencia")
    parser.add_argument("--request-bank", type=Path, required=True,
                        help="JSON sintético: caseId, token, direccionId, metodopagoId")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "resultados-microservicios")
    parser.add_argument("--concurrencia", type=int, default=50)
    parser.add_argument("--repeticion", type=int, default=1)
    parser.add_argument("--warmup-seconds", type=float, default=60.0)
    parser.add_argument("--timeout-seconds", type=float, default=30.0)
    args = parser.parse_args()

    cases = json.loads(args.request_bank.read_text(encoding="utf-8"))
    if not isinstance(cases, list) or not cases:
        raise SystemExit("request-bank debe ser una lista JSON no vacía")
    required = {"caseId", "token", "direccionId", "metodopagoId"}
    if any(not required.issubset(case) for case in cases):
        raise SystemExit(f"cada caso requiere: {', '.join(sorted(required))}")

    status, snapshot, _ = request(f"{args.gateway.rstrip('/')}/api/admin/system", args.admin_token)
    if status != 200:
        raise SystemExit(f"preflight del stack falló con HTTP {status}: {snapshot}")
    data = snapshot.get("data", snapshot)
    down = [service["service"] for service in data.get("services", []) if service.get("status") != "UP"]
    if down:
        raise SystemExit("servicios no disponibles: " + ", ".join(down))

    gateway = args.gateway.rstrip("/")
    warmup(gateway, args.admin_token, args.warmup_seconds)
    selected = cases[:args.concurrencia]
    started_at = time.time()
    with ThreadPoolExecutor(max_workers=args.concurrencia) as pool:
        rows = list(pool.map(lambda case: checkout(gateway, case, args.timeout_seconds), selected))
    args.output.mkdir(parents=True, exist_ok=True)
    output = args.output / f"microservices-c{args.concurrencia}-r{args.repeticion}.csv"
    with output.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader(); writer.writerows(rows)
    metadata = {"gateway": gateway, "coord_declarada": data.get("coordination"),
                "coord_source": data.get("coordinationSource"), "concurrencia": args.concurrencia,
                "repeticion": args.repeticion, "warmup_seconds": args.warmup_seconds,
                "started_at_epoch": started_at, "operations": len(rows),
                "successful": sum(bool(row["success"]) for row in rows),
                "note": "COORD es configuración declarada; validar por separado que gobierne el coordinador productivo."}
    output.with_suffix(".metadata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
