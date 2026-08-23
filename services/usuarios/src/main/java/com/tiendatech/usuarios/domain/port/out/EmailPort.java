package com.tiendatech.usuarios.domain.port.out;

public interface EmailPort {
    void send(String recipient, String subject, String body);
}
