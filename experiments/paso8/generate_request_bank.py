#!/usr/bin/env python3
"""Genera el banco de usuarios sinteticos para el experimento real (Paso 8).

Registra usuarios, inicia sesion, crea direccion y metodo de pago contra el
stack real via el API Gateway. NO pre-llena el carrito: eso lo hace
checkout_locustfile.py en cada iteracion, porque el checkout consume el
carrito y el experimento hace muchas compras por usuario a lo largo de 5.4h.

Guarda usuario/contrasena (no solo el token) porque el JWT de acceso expira a
los 10 minutos (auth.access.minutes=10 en usuarios-service) y el experimento
dura horas: checkout_locustfile.py vuelve a loguearse cuando ve un 401.

El JSON de salida no se versiona (contiene credenciales validas, aunque sean
sinteticas). Ver experiments/paso8/README.md.
"""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path


def call(gateway: str, method: str, path: str, token: str | None = None,
         body: dict | None = None, timeout: float = 15) -> tuple[int, object, dict]:
    url = f"{gateway}{path}"
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            parsed = json.loads(raw) if raw else {}
            return resp.status, parsed, dict(resp.headers)
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8")
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"message": raw}
        return error.code, parsed, dict(error.headers)


def unwrap(body: object) -> object:
    """El gateway envuelve TODA respuesta como {"status":,"data":<payload>,"message":,...}.
    Verificado con curl real el 2026-09-04 contra /api/ciudades, /api/login,
    /api/usuarios/crear (que ademas trae un envoltorio propio DENTRO de "data"),
    /api/metodopago/tipos y /api/usuarios/{id}/direcciones. No es una suposicion."""
    if isinstance(body, dict) and "data" in body and "status" in body:
        return body["data"]
    return body


def obtener_ciudad_id(gateway: str) -> int:
    status, body, _ = call(gateway, "GET", "/api/ciudades")
    payload = unwrap(body)
    if status != 200 or not payload:
        raise SystemExit(f"no se pudieron listar ciudades: HTTP {status} {body}")
    return int(payload[0]["ciudadId"])


def obtener_tipo_pago_id(gateway: str, token: str) -> int:
    """/api/metodopago/tipos exige JWT (verificado: HTTP 401 'JWT requerido' sin
    token). Por eso necesita el token de un usuario ya logueado, no se puede
    resolver antes del primer login."""
    status, body, _ = call(gateway, "GET", "/api/metodopago/tipos", token=token)
    payload = unwrap(body)
    if status != 200 or not payload:
        raise SystemExit(f"no se pudieron listar tipos de metodo de pago: HTTP {status} {body}")
    primero = payload[0]
    valor = primero.get("tipoId", primero.get("id"))
    if valor is None:
        raise SystemExit(f"no se encontro un id de tipo en /api/metodopago/tipos: {primero}")
    return int(valor)


def obtener_productos_con_stock(gateway: str, minimo_stock: int, cuantos: int) -> list[int]:
    status, body, _ = call(gateway, "GET", "/api/productos?page=0&size=200")
    payload = unwrap(body)
    if status != 200:
        raise SystemExit(f"no se pudo listar productos: HTTP {status}")
    candidatos = [p for p in payload if p.get("habilitado") and (p.get("stock") or 0) >= minimo_stock]
    if len(candidatos) < cuantos:
        disponibles = sorted((p.get("stock") or 0) for p in payload if p.get("habilitado"))
        raise SystemExit(
            f"solo hay {len(candidatos)} productos habilitados con stock >= {minimo_stock} "
            f"(se necesitan {cuantos}). Stocks habilitados disponibles: {disponibles[-10:]}. "
            f"Corre reset_ambiente.py --topar-stock primero, o baja --min-stock-producto.")
    return [int(p["producto_id"]) for p in candidatos[:cuantos]]


def crear_usuario(gateway: str, idx: int, seed: int, pace: float) -> dict:
    sufijo = f"{seed}{idx:05d}"
    usuario = f"cargasint{sufijo}"
    contrasena = f"CargaSint{sufijo}Aa1!"
    payload = {
        "nombre": f"Carga Sintetica {idx}",
        "cedula": (f"1{sufijo}0000000000")[:10],
        "correo": f"{usuario}@carga.tiendatech.test",
        "telefono": (f"09{sufijo}0000000000")[:10],
        "contrasena": contrasena,
        "usuario": usuario,
    }
    status, body, _ = call(gateway, "POST", "/api/usuarios/crear", body=payload)
    time.sleep(pace)
    if status not in (200, 201):
        return {"ok": False, "idx": idx, "etapa": "crear", "detalle": f"HTTP {status}: {body}"}
    return {"ok": True, "usuario": usuario, "contrasena": contrasena}


def login(gateway: str, usuario: str, contrasena: str, pace: float) -> tuple[str | None, int | None, str | None]:
    status, body, _ = call(gateway, "POST", "/api/login", body={"usuario": usuario, "contrasena": contrasena})
    time.sleep(pace)
    if status != 200:
        return None, None, f"HTTP {status}: {body}"
    payload = unwrap(body)
    token = payload.get("token") or payload.get("access")
    usuario_id = (payload.get("user") or {}).get("usuarioId")
    if not token or usuario_id is None:
        return None, None, f"respuesta de login sin token/usuarioId: {body}"
    return token, int(usuario_id), None


def crear_direccion(gateway: str, token: str, usuario_id: int, ciudad_id: int, pace: float) -> tuple[int | None, str | None]:
    payload = {"calle": "Av. Carga Sintetica S/N", "referencia": "generado por paso8/generate_request_bank.py",
               "ciudadId": ciudad_id}
    status, body, _ = call(gateway, "POST", f"/api/usuarios/{usuario_id}/direcciones", token=token, body=payload)
    time.sleep(pace)
    if status not in (200, 201):
        return None, f"HTTP {status}: {body}"
    direccion_id = body.get("direccionId") or (body.get("data") or {}).get("direccionId")
    if direccion_id is None:
        return None, f"respuesta de direccion sin direccionId: {body}"
    return int(direccion_id), None


def crear_metodo_pago(gateway: str, token: str, tipo_id: int, pace: float) -> tuple[int | None, str | None]:
    payload = {"numeroTarjeta": "4111111111111111", "fechaExpiracion": "2031-12-31", "tipoId": tipo_id}
    status, body, headers = call(gateway, "POST", "/api/metodopago", token=token, body=payload)
    time.sleep(pace)
    if status not in (200, 201):
        return None, f"HTTP {status}: {body}"
    location = headers.get("Location") or headers.get("location")
    if location:
        return int(location.rstrip("/").split("/")[-1]), None
    metodopago_id = (body or {}).get("metodopagoId")
    if metodopago_id is None:
        return None, f"no se pudo extraer metodopagoId ni de Location ni del cuerpo: {body}"
    return int(metodopago_id), None


def parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser()
    ap.add_argument("--gateway", default="http://localhost:8180")
    ap.add_argument("--output", type=Path, required=True)
    ap.add_argument("--count", type=int, default=400)
    ap.add_argument("--start-index", type=int, default=0,
                    help="indice inicial del usuario sintetico; util para continuar un banco parcial")
    ap.add_argument("--seed", type=int, default=2026)
    ap.add_argument("--pace-seconds", type=float, default=0.15,
                     help="pausa entre llamadas HTTP para no saturar el rate-limit del gateway")
    ap.add_argument("--min-stock-producto", type=int, default=200_000,
                     help="stock minimo exigido a cada producto candidato; correr reset_ambiente.py antes")
    ap.add_argument("--productos-a-usar", type=int, default=3)
    ap.add_argument("--producto-ids", type=int, nargs="+",
                    help="IDs ya elegidos y con stock repuesto; evita listar /api/productos")
    return ap


def main() -> int:
    args = parser().parse_args()

    ciudad_id = obtener_ciudad_id(args.gateway)
    productos = args.producto_ids or obtener_productos_con_stock(
        args.gateway, args.min_stock_producto, args.productos_a_usar)
    print(f"ciudad_id={ciudad_id} productos={productos}")

    tipo_pago_id: int | None = None  # se resuelve con el token del primer usuario (el endpoint exige JWT)
    casos = []
    fallidos = []
    for i in range(args.start_index, args.start_index + args.count):
        creado = crear_usuario(args.gateway, i, args.seed, args.pace_seconds)
        if not creado["ok"]:
            fallidos.append(creado)
            continue
        token, usuario_id, error = login(args.gateway, creado["usuario"], creado["contrasena"], args.pace_seconds)
        if error:
            fallidos.append({"idx": i, "etapa": "login", "detalle": error})
            continue
        if tipo_pago_id is None:
            tipo_pago_id = obtener_tipo_pago_id(args.gateway, token)
            print(f"tipo_pago_id={tipo_pago_id}")
        direccion_id, error = crear_direccion(args.gateway, token, usuario_id, ciudad_id, args.pace_seconds)
        if error:
            fallidos.append({"idx": i, "etapa": "direccion", "detalle": error})
            continue
        metodopago_id, error = crear_metodo_pago(args.gateway, token, tipo_pago_id, args.pace_seconds)
        if error:
            fallidos.append({"idx": i, "etapa": "metodopago", "detalle": error})
            continue
        casos.append({
            "caseId": i + 1,
            "usuario": creado["usuario"],
            "contrasena": creado["contrasena"],
            "usuarioId": usuario_id,
            "token": token,
            "direccionId": direccion_id,
            "metodopagoId": metodopago_id,
            "productoId": productos[i % len(productos)],
        })
        if (len(casos)) % 25 == 0:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(casos, indent=2) + "\n", encoding="utf-8")
            print(f"[{len(casos)}/{args.count}] generados, {len(fallidos)} fallidos hasta ahora")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(casos, indent=2) + "\n", encoding="utf-8")
    if fallidos:
        fallos_path = args.output.with_suffix(".fallidos.json")
        fallos_path.write_text(json.dumps(fallidos, indent=2) + "\n", encoding="utf-8")
        print(f"{len(fallidos)} usuarios fallaron, detalle en {fallos_path}")
    print(f"banco escrito con {len(casos)} casos en {args.output}")
    if len(casos) < args.count:
        print(f"ADVERTENCIA: se pidieron {args.count} casos y solo se generaron {len(casos)}; "
              f"revisa los fallidos antes de usar este banco para la concurrencia mas alta")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
