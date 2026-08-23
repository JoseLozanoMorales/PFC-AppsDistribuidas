package com.tiendatech.usuarios.application.service.auth;

import com.tiendatech.usuarios.domain.port.out.OnlineUserPort;
import org.springframework.stereotype.Service;

@Service
public class UserOnlineService {
    private final OnlineUserPort onlineUserPort;

    public UserOnlineService(OnlineUserPort onlineUserPort) {
        this.onlineUserPort = onlineUserPort;
    }

    public boolean isOnline(String username){
        return onlineUserPort.isOnline(username);
    }
}
