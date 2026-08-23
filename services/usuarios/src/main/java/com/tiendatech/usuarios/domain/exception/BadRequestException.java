package com.tiendatech.usuarios.domain.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String msg) { super(msg); }
}
