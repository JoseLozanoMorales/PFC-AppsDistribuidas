package com.tiendatech.usuarios.infrastructure.security;

import com.tiendatech.usuarios.domain.port.out.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordHasherAdapter implements PasswordHasher {
    private final PasswordEncoder encoder;

    @Override public String hash(String rawPassword) { return encoder.encode(rawPassword); }
    @Override public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
