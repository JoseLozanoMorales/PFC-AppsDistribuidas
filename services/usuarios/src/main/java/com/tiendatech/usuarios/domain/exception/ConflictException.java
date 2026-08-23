package com.tiendatech.usuarios.domain.exception;

public class ConflictException extends RuntimeException {
    public ConflictException(String msg) { super(msg); }
}
