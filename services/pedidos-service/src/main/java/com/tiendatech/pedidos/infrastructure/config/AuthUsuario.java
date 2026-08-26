package com.tiendatech.pedidos.infrastructure.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resuelve la identidad confiable propagada por el gateway (ADR-007) desde las
 * cabeceras X-User-Id / X-Usuario / X-User-Role hacia un {@link AuthenticatedUser}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUsuario {
}
