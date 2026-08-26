package com.tiendatech.ordenesproveedores.presentation;

import com.tiendatech.ordenesproveedores.infrastructure.config.PgErrorMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    // Debe ir explicito: si no, el handler generico de Exception de mas abajo la atraparia
    // primero y la convertiria en 500 en lugar de respetar el status que ya trae.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    // BadSqlGrammarException es DataAccessException, pero la distinguimos: si esto salta
    // es un bug nuestro (SQL mal armado), no un error de negocio del usuario.
    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<Map<String, Object>> handleBadSql(BadSqlGrammarException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error interno de SQL", "detail", rootMessage(ex)));
    }

    // Camino "limpio": procedimientos llamados sin try/catch en el repository
    // (enviar, cancelar, proveedor.*) propagan DataAccessException directo.
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex) {
        HttpStatus status = PgErrorMapper.statusFor(ex);
        return ResponseEntity.status(status)
                .body(Map.of("error", PgErrorMapper.messageFor(ex)));
    }

    // Camino "envuelto": crear/actualizar/registrarRecepcion en OrdenCompraRepository
    // atrapan la excepcion y la relanzan como IllegalStateException(mensaje, causaOriginal).
    // La causa real (con el SQLSTATE) sigue viva en getCause(), asi que la desenvolvemos igual.
    //
    // Camino "regla de negocio pura": exigirEstado y validaciones de estado en
    // OrdenCompraRepository lanzan IllegalStateException sin causa (no hay excepcion de
    // base de datos detras). Ahi no tiene sentido preguntarle a PgErrorMapper -- siempre
    // devuelve 500 porque no encuentra SQLSTATE. Es un 409: el estado actual del recurso
    // no permite la operacion pedida.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        if (ex.getCause() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
        HttpStatus status = PgErrorMapper.statusFor(ex);
        return ResponseEntity.status(status)
                .body(Map.of("error", PgErrorMapper.messageFor(ex)));
    }

    // @Valid en el controller (Bean Validation sobre el body, ej. Proveedor.ruc).
    // Debe ir explicito por la misma razon que ResponseStatusException: si no,
    // el handler generico de Exception la convertiria en 500.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                campos.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Datos invalidos", "campos", campos));
    }

    // Validaciones hechas en Java (service/DTO), no en la BD.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error inesperado", "detail", ex.getMessage()));
    }

    private String rootMessage(DataAccessException ex) {
        return ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
    }
}
