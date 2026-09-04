"""Verifica copias y reproduce la tabla de stock, sin modificar las bases."""
import csv
import hashlib
import json
import sqlite3
from pathlib import Path


def main():
    root = Path(__file__).resolve().parent
    provenance = json.loads((root / 'procedencia.json').read_text(encoding='utf-8'))
    for item in provenance['files']:
        actual = hashlib.sha256((root / item['copy']).read_bytes()).hexdigest()
        if actual != item['sha256']:
            raise ValueError('Copia alterada: ' + item['copy'])
    with (root / 'pilot.csv').open(encoding='utf-8', newline='') as stream:
        rows = list(csv.DictReader(stream))
    result = {'commit': provenance['commit'], 'operaciones': len(rows), 'estrategias': {}}
    for coord in ('2pc', 'saga'):
        uri = (root / f'pilot-{coord}.db').resolve().as_uri() + '?mode=ro'
        with sqlite3.connect(uri, uri=True) as db:
            stock = db.execute('''SELECT i.product_id, i.initial_stock, i.stock,
                i.initial_stock + COALESCE(SUM(m.delta), 0)
                FROM inventory i LEFT JOIN stock_movements m ON m.product_id=i.product_id
                GROUP BY i.product_id''').fetchall()
        report = json.loads((root / f'oracle-{coord}.json').read_text(encoding='utf-8'))
        recorded = next(c for c in report['checks'] if c['check'] == 'stock_cuadra_con_movimientos')
        violations = sum(final != expected for _, _, final, expected in stock)
        if recorded['violations'] != violations or recorded['pass'] != (violations == 0):
            raise ValueError('El informe no coincide con la base: ' + coord)
        result['estrategias'][coord] = {
            'operaciones_csv': sum(r['coord'] == coord for r in rows),
            'columnas': ['producto', 'inicial', 'final', 'inicial_mas_movimientos'],
            'stock': stock,
            'filas_discordantes': violations,
        }
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
