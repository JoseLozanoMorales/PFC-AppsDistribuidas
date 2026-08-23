package org.example.domain;

/**
 * Otra peticion concurrente con la misma (usuario_id, clave) ya se registro
 * primero. No es un error del negocio: el llamador debe releer la fila
 * ganadora con {@link IdempotenciaRepository#buscarPorUsuarioYClave} y devolver
 * ese resultado en vez de reintentar la escritura.
 */
public class ClaveIdempotenciaEnConflictoException extends RuntimeException {

    public ClaveIdempotenciaEnConflictoException(String message, Throwable cause) {
        super(message, cause);
    }
}
