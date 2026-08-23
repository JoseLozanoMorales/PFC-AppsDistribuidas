package com.tiendatech.usuarios.infrastructure.security;

import com.tiendatech.usuarios.domain.port.out.OnlineUserPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class SpringSessionOnlineUserAdapter implements OnlineUserPort {
    @Autowired(required = false)
    private SessionRegistry sessionRegistry;

    @Override
    public boolean isOnline(String username) {
        if (username == null || username.isBlank() || sessionRegistry == null) return false;
        return sessionRegistry.getAllPrincipals().stream().anyMatch(principal ->
                (principal instanceof UserDetails user && user.getUsername().equalsIgnoreCase(username))
                        || (!(principal instanceof UserDetails)
                        && principal.toString().equalsIgnoreCase(username)));
    }
}
