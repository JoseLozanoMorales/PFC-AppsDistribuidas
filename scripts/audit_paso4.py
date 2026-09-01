"""Preauditoría estática del Paso 4; los hallazgos requieren revisión humana."""
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OWNERS = {
    "inventario-service": "inventario", "productos-service": "productos",
    "pedidos-service": "pedidos", "ventas-service": "ventas",
    "ordenes-proveedores-service": "ordenes_proveedores", "usuarios": "usuarios",
}


def main():
    sql = re.compile(
        r"\b(?:FROM|JOIN|UPDATE|INTO|DELETE\s+FROM)\s+"
        r"(productos|inventario|pedidos|ventas|usuarios|ordenes_proveedores)\.", re.I)
    cross_schema = []
    for service, owner in OWNERS.items():
        source = ROOT / "services" / service / "src"
        for file in source.rglob("*.java"):
            for number, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
                for match in sql.finditer(line):
                    if match.group(1).lower() != owner:
                        cross_schema.append({
                            "file": file.relative_to(ROOT).as_posix(),
                            "line": number,
                            "owner": owner,
                            "foreign_schema": match.group(1).lower(),
                        })

    dockerfiles = list((ROOT / "services").glob("*/Dockerfile"))
    dockerfiles.append(ROOT / "Apps/web/frontend/Dockerfile")
    stages = {
        file.relative_to(ROOT).as_posix(): len(re.findall(
            r"^FROM\s", file.read_text(encoding="utf-8"), re.M | re.I))
        for file in dockerfiles if file.exists()
    }
    env_text = (ROOT / ".env.example").read_text(encoding="utf-8")
    documented = set(re.findall(r"^([A-Z][A-Z0-9_]+)=", env_text, re.M))
    compose = "\n".join(
        line for line in (ROOT / "docker-compose.yml").read_text(encoding="utf-8").splitlines()
        if not line.lstrip().startswith("#")
    )
    referenced = set(re.findall(r"\$\{([A-Z][A-Z0-9_]+)", compose))
    specs = sorted(str(p.relative_to(ROOT)) for p in (ROOT / "docs/api").glob("*.yaml"))
    schema_text = (ROOT / "docs/db/schema.sql").read_text(encoding="utf-8")
    cross_schema_fks = []
    current_schema = None
    for number, line in enumerate(schema_text.splitlines(), 1):
        create = re.search(r"CREATE TABLE IF NOT EXISTS\s+(\w+)\.", line, re.I)
        if create:
            current_schema = create.group(1).lower()
        target = re.search(r"REFERENCES\s+(\w+)\.", line, re.I)
        if current_schema and target and target.group(1).lower() != current_schema:
            cross_schema_fks.append({"line": number, "owner": current_schema,
                                     "foreign_schema": target.group(1).lower()})

    print(json.dumps({
        "warning": "Static candidates only; runtime verification is still required.",
        "cross_schema_candidates": cross_schema,
        "cross_schema_foreign_keys": cross_schema_fks,
        "dockerfile_stage_counts": stages,
        "compose_variables_missing_from_env_example": sorted(referenced - documented),
        "openapi_yaml_files": specs,
    }, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
