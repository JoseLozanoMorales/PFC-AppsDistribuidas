#!/usr/bin/env python3
"""Banco reproducible para comparar 2PC y Saga en el checkout de TiendaTech."""

from __future__ import annotations

import argparse
import csv
import json
import os
import random
import sqlite3
import time
from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass(frozen=True)
class Case:
    case_id: int
    product_id: int
    quantity: int
    amount: int
    fault: str
    fault_probability: float
    seed: int


class FaultInjector:
    """Pasarela intermedia determinista: normal, omisión o temporización."""

    def __init__(self, mode: str, probability: float, seed: int, delay_seconds: float = 5.0):
        self.mode = mode
        self.probability = probability
        self.delay_seconds = delay_seconds
        self.seed = seed

    def authorize(self, operation_seed: int) -> bool:
        # La decisión pertenece al caso, no al orden en que el scheduler toma hilos.
        inject = random.Random(self.seed ^ operation_seed).random() < self.probability
        if not inject or self.mode == "none":
            return True
        if self.mode == "timing":
            time.sleep(self.delay_seconds)
        raise TimeoutError("pasarela sin respuesta" if self.mode == "omission" else "respuesta posterior al timeout")


SCHEMA = """
CREATE TABLE inventory(product_id INTEGER PRIMARY KEY, initial_stock INTEGER NOT NULL, stock INTEGER NOT NULL);
CREATE TABLE orders(tx_id TEXT PRIMARY KEY, case_id INTEGER, product_id INTEGER, quantity INTEGER,
                    amount INTEGER, status TEXT NOT NULL);
CREATE TABLE payments(tx_id TEXT PRIMARY KEY, amount INTEGER NOT NULL, status TEXT NOT NULL);
CREATE TABLE stock_movements(id INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, product_id INTEGER,
                             delta INTEGER NOT NULL, stock_after INTEGER NOT NULL, kind TEXT NOT NULL);
CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, tx_id TEXT, participant TEXT,
                    phase TEXT, outcome TEXT, elapsed_ms REAL);
"""


class Lab:
    def __init__(self, db_path: Path, coord: str, injector: FaultInjector):
        if coord not in {"2pc", "saga"}:
            raise ValueError("COORD debe ser 2pc o saga")
        self.db_path, self.coord, self.injector = db_path, coord, injector

    def connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.db_path, timeout=30, isolation_level=None)
        connection.execute("PRAGMA foreign_keys=ON")
        connection.execute("PRAGMA busy_timeout=30000")
        return connection

    def reset(self, initial_stock: int = 60) -> None:
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        if self.db_path.exists():
            self.db_path.unlink()
        with closing(self.connect()) as db:
            db.executescript(SCHEMA)
            db.execute("INSERT INTO inventory VALUES (1, ?, ?)", (initial_stock, initial_stock))

    def event(self, db: sqlite3.Connection, tx: str, participant: str, phase: str,
              outcome: str, started: float) -> None:
        db.execute("INSERT INTO events(tx_id,participant,phase,outcome,elapsed_ms) VALUES(?,?,?,?,?)",
                   (tx, participant, phase, outcome, (time.perf_counter() - started) * 1000))

    def purchase(self, case: Case) -> dict:
        # El identificador también es reproducible: cada base se reinicia por estrategia.
        tx, started = f"seed-{case.seed}-case-{case.case_id}", time.perf_counter()
        ok, error = False, ""
        try:
            if self.coord == "2pc":
                self.two_phase_commit(tx, case, started)
            else:
                self.saga(tx, case, started)
            ok = True
        except (TimeoutError, ValueError, sqlite3.Error) as exc:
            error = str(exc)
        return {"case_id": case.case_id, "tx_id": tx, "coord": self.coord,
                "success": ok, "fault": case.fault, "elapsed_ms": round((time.perf_counter()-started)*1000, 3),
                "error": error}

    def two_phase_commit(self, tx: str, case: Case, started: float) -> None:
        # BEGIN IMMEDIATE representa el bloqueo conservado desde PREPARE hasta COMMIT/ROLLBACK.
        with closing(self.connect()) as db:
            db.execute("BEGIN IMMEDIATE")
            try:
                stock = db.execute("SELECT stock FROM inventory WHERE product_id=?", (case.product_id,)).fetchone()
                if stock is None or stock[0] < case.quantity:
                    raise ValueError("inventario votó NO")
                self.event(db, tx, "inventario", "prepare", "YES", started)
                self.injector.authorize(case.seed)
                self.event(db, tx, "pagos", "prepare", "YES", started)
                self.event(db, tx, "ordenes", "prepare", "YES", started)
                new_stock = stock[0] - case.quantity
                db.execute("UPDATE inventory SET stock=? WHERE product_id=?", (new_stock, case.product_id))
                db.execute("INSERT INTO stock_movements(tx_id,product_id,delta,stock_after,kind) VALUES(?,?,?,?,?)",
                           (tx, case.product_id, -case.quantity, new_stock, "commit"))
                db.execute("INSERT INTO payments VALUES(?,?,?)", (tx, case.amount, "CAPTURED"))
                db.execute("INSERT INTO orders VALUES(?,?,?,?,?,?)",
                           (tx, case.case_id, case.product_id, case.quantity, case.amount, "CONFIRMED"))
                self.event(db, tx, "coordinator", "commit", "COMMIT", started)
                db.commit()
            except Exception:
                db.rollback()
                raise

    def saga(self, tx: str, case: Case, started: float) -> None:
        # Cada paso confirma inmediatamente; los ya confirmados se deshacen en orden inverso.
        with closing(self.connect()) as db:
            stock = db.execute("SELECT stock FROM inventory WHERE product_id=?", (case.product_id,)).fetchone()
            if stock is None or stock[0] < case.quantity:
                raise ValueError("stock insuficiente")
            new_stock = stock[0] - case.quantity
            db.execute("BEGIN IMMEDIATE")
            db.execute("UPDATE inventory SET stock=? WHERE product_id=?", (new_stock, case.product_id))
            db.execute("INSERT INTO stock_movements(tx_id,product_id,delta,stock_after,kind) VALUES(?,?,?,?,?)",
                       (tx, case.product_id, -case.quantity, new_stock, "reserve"))
            db.execute("INSERT INTO orders VALUES(?,?,?,?,?,?)",
                       (tx, case.case_id, case.product_id, case.quantity, case.amount, "PENDING"))
            self.event(db, tx, "inventario", "saga", "COMMIT", started)
            db.commit()
            try:
                self.injector.authorize(case.seed)
                db.execute("INSERT INTO payments VALUES(?,?,?)", (tx, case.amount, "CAPTURED"))
                self.event(db, tx, "pagos", "saga", "COMMIT", started)
                db.execute("UPDATE orders SET status='CONFIRMED' WHERE tx_id=?", (tx,))
                self.event(db, tx, "ordenes", "saga", "COMMIT", started)
            except Exception:
                restored = db.execute("SELECT stock FROM inventory WHERE product_id=?", (case.product_id,)).fetchone()[0] + case.quantity
                db.execute("UPDATE inventory SET stock=? WHERE product_id=?", (restored, case.product_id))
                db.execute("INSERT INTO stock_movements(tx_id,product_id,delta,stock_after,kind) VALUES(?,?,?,?,?)",
                           (tx, case.product_id, case.quantity, restored, "compensate"))
                db.execute("UPDATE payments SET status='REVERSED' WHERE tx_id=?", (tx,))
                db.execute("UPDATE orders SET status='COMPENSATED' WHERE tx_id=?", (tx,))
                self.event(db, tx, "coordinator", "compensate", "DONE", started)
                raise


def oracle(db_path: Path) -> dict:
    checks = []
    with closing(sqlite3.connect(db_path)) as db:
        queries = [
            ("pago_exacto", """SELECT count(*) FROM orders o LEFT JOIN payments p USING(tx_id)
               WHERE o.status='CONFIRMED' AND (p.status!='CAPTURED' OR p.amount!=o.amount OR p.tx_id IS NULL)"""),
            ("stock_una_vez", """SELECT count(*) FROM orders o WHERE o.status='CONFIRMED' AND
               (SELECT COALESCE(sum(delta),0) FROM stock_movements m WHERE m.tx_id=o.tx_id)!=-o.quantity"""),
            ("stock_nunca_negativo", "SELECT count(*) FROM stock_movements WHERE stock_after < 0"),
            ("stock_cuadra_con_movimientos", """SELECT count(*) FROM inventory i WHERE i.stock !=
               i.initial_stock + (SELECT COALESCE(sum(delta),0) FROM stock_movements m
                                  WHERE m.product_id=i.product_id)"""),
            ("compensacion_completa", """SELECT count(*) FROM orders o WHERE o.status IN ('COMPENSATED','CANCELLED') AND
               ((SELECT COALESCE(sum(delta),0) FROM stock_movements m WHERE m.tx_id=o.tx_id)!=0 OR
                EXISTS(SELECT 1 FROM payments p WHERE p.tx_id=o.tx_id AND p.status IN ('PENDING','CAPTURED')))"""),
        ]
        for name, query in queries:
            violations = db.execute(query).fetchone()[0]
            checks.append({"check": name, "pass": violations == 0, "violations": violations})
    return {"pass": all(item["pass"] for item in checks), "checks": checks}


def make_cases(count: int, seed: int, probability: float = 0.35,
               faults: tuple[str, ...] = ("none", "omission", "timing")) -> list[Case]:
    rng = random.Random(seed)
    return [Case(i + 1, 1, rng.randint(1, 3), 1000 * rng.randint(1, 9), faults[i % len(faults)],
                 probability if faults[i % len(faults)] != "none" else 0.0, seed * 1000 + i)
            for i in range(count)]


def compatibility_cases(count: int, seed: int) -> list[dict]:
    """Oráculo conocido del asistente: socket, RAM y margen eléctrico de 1.7x."""
    rng = random.Random(seed)
    sockets = ("AM5", "LGA1700")
    result = []
    for case_id in range(1, count + 1):
        cpu_socket = rng.choice(sockets)
        motherboard_socket = rng.choice(sockets)
        ram_gb = rng.choice((8, 16, 32, 64))
        cpu_tdp, gpu_tdp = rng.choice((65, 105, 125, 170)), rng.choice((115, 165, 250, 285))
        psu_watts = rng.choice((450, 550, 650, 750, 1000))
        reasons = []
        if cpu_socket != motherboard_socket:
            reasons.append("socket_incompatible")
        if ram_gb < 16:
            reasons.append("ram_inferior_16gb")
        if psu_watts < (cpu_tdp + gpu_tdp) * 1.7:
            reasons.append("fuente_sin_margen_1_7x")
        result.append({"case_id": case_id, "cpu_socket": cpu_socket,
                       "motherboard_socket": motherboard_socket, "ram_gb": ram_gb,
                       "cpu_tdp": cpu_tdp, "gpu_tdp": gpu_tdp, "psu_watts": psu_watts,
                       "expected_compatible": not reasons, "expected_reasons": reasons})
    return result


def run(args: argparse.Namespace) -> int:
    cases = make_cases(args.cases, args.seed, args.fault_probability)
    args.output.mkdir(parents=True, exist_ok=True)
    all_rows = []
    for coord in args.coords:
        # Un inyector por tipo preserva la secuencia determinista aun con concurrencia.
        lab = Lab(args.output / f"pilot-{coord}.db", coord, FaultInjector("none", 0, args.seed))
        lab.reset(args.initial_stock)
        for fault in ("none", "omission", "timing"):
            group = [case for case in cases if case.fault == fault]
            lab.injector = FaultInjector(fault, group[0].fault_probability if group else 0,
                                         args.seed + len(fault), args.delay_seconds)
            with ThreadPoolExecutor(max_workers=args.workers) as pool:
                all_rows.extend(pool.map(lab.purchase, group))
        report = oracle(lab.db_path)
        (args.output / f"oracle-{coord}.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    with (args.output / "pilot.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(all_rows[0]))
        writer.writeheader(); writer.writerows(all_rows)
    transaction_bank = [asdict(case) | {"expected_without_fault": "CONFIRMED"}
                        for case in make_cases(120, args.seed, args.fault_probability)]
    (args.output / "transaction-case-bank.json").write_text(
        json.dumps(transaction_bank, indent=2) + "\n", encoding="utf-8")
    (args.output / "compatibility-case-bank.json").write_text(
        json.dumps(compatibility_cases(120, args.seed), indent=2) + "\n", encoding="utf-8")
    return 0


def parser() -> argparse.ArgumentParser:
    cli = argparse.ArgumentParser()
    cli.add_argument("--coords", nargs="+", default=[os.getenv("COORD", "2pc")], choices=["2pc", "saga"])
    cli.add_argument("--cases", type=int, default=30)
    cli.add_argument("--seed", type=int, default=int(os.getenv("FAULT_SEED", "2026")))
    cli.add_argument("--fault-probability", type=float,
                     default=float(os.getenv("FAULT_PROBABILITY", "0.35")))
    cli.add_argument("--workers", type=int, default=6)
    cli.add_argument("--initial-stock", type=int, default=60)
    cli.add_argument("--delay-seconds", type=float, default=float(os.getenv("FAULT_DELAY_SECONDS", "5")))
    cli.add_argument("--output", type=Path, default=Path(__file__).parent / "evidence")
    return cli


if __name__ == "__main__":
    raise SystemExit(run(parser().parse_args()))
