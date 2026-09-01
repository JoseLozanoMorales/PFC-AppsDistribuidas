#!/usr/bin/env python3
"""Ejecuta las celdas de código del cuaderno del Paso 8 sin depender de Jupyter."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("notebook", type=Path, nargs="?", default=Path(__file__).with_name("analisis.ipynb"))
    args = parser.parse_args()
    notebook = args.notebook.resolve()
    data = json.loads(notebook.read_text(encoding="utf-8"))
    namespace = {"__name__": "__notebook__"}
    previous = Path.cwd()
    try:
        os.chdir(notebook.parent)
        for index, cell in enumerate(data.get("cells", []), start=1):
            if cell.get("cell_type") != "code":
                continue
            source = "".join(cell.get("source", []))
            exec(compile(source, f"{notebook.name}:cell-{index}", "exec"), namespace)
    finally:
        os.chdir(previous)
    print(f"Cuaderno ejecutado correctamente: {notebook}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

