"""
Identidad tomada de X-User-Id/X-Usuario/X-User-Role (ADR-005) cuando este
servicio no necesita bloquear al llamador si faltan: el gateway ya protege
/api/** asi que en la practica siempre llegan, pero este endpoint no persiste
nada por usuario y no tiene motivo para exigirlas. Si vienen, se usan solo
para logging del analisis. Un X-User-Id malformado nunca bloquea la
peticion, simplemente se registra como anonimo.
"""
from dataclasses import dataclass

from fastapi import Header


@dataclass(frozen=True)
class IdentidadOpcional:
    user_id: int | None
    username: str | None
    role: str | None

    @property
    def presente(self) -> bool:
        return self.user_id is not None or self.username is not None or self.role is not None

    def __str__(self) -> str:
        return f"userId={self.user_id}, usuario={self.username}" if self.presente else "anonimo"


def identidad_opcional(
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
    x_usuario: str | None = Header(default=None, alias="X-Usuario"),
    x_user_role: str | None = Header(default=None, alias="X-User-Role"),
) -> IdentidadOpcional:
    user_id: int | None = None
    if x_user_id is not None and x_user_id.strip():
        try:
            user_id = int(x_user_id.strip())
        except ValueError:
            user_id = None
    return IdentidadOpcional(user_id=user_id, username=x_usuario, role=x_user_role)
