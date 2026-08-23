package com.tiendatech.usuarios.domain.port.out;

import com.tiendatech.usuarios.domain.model.AdminUserCommand;
import java.util.List;

public interface AdminUserPort {
    void execute(List<AdminUserCommand> commands);
}
