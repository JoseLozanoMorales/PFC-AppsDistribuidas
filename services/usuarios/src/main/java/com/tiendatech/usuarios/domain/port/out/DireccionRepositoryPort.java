package com.tiendatech.usuarios.domain.port.out;

import com.tiendatech.usuarios.domain.model.Direccion;
import java.util.List;

public interface DireccionRepositoryPort {
    List<Direccion> findEnabledByUser(Integer usuarioId);
    List<Direccion> findDetailedByUser(Integer usuarioId);
    Direccion create(Integer usuarioId, Short ciudadId, String calle, String referencia);
    Direccion update(Integer usuarioId, Short direccionId, Short ciudadId, String calle, String referencia);
    void disable(Integer usuarioId, Short direccionId);
}
