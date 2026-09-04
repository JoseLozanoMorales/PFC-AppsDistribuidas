package com.tiendatech.usuarios.application.service;

import com.tiendatech.usuarios.application.dto.ProvinciaDTO;
import com.tiendatech.usuarios.domain.port.out.ProvinciaRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProvinciaService {

    private static final long CACHE_MILLIS = 60_000;

    private final ProvinciaRepositoryPort repo;
    private volatile List<ProvinciaDTO> cachedProvincias = List.of();
    private volatile long cacheExpiresAt;

    @Transactional(readOnly = true)
    public synchronized List<ProvinciaDTO> listar() {
        long now = System.currentTimeMillis();
        if (now < cacheExpiresAt) return cachedProvincias;
        cachedProvincias = repo.findAllByName().stream()
                .map(e -> {
                    ProvinciaDTO d = new ProvinciaDTO();
                    d.setProvinciaId(e.id());
                    d.setNombre(e.nombre());
                    return d;
                })
                .collect(Collectors.toList());
        cacheExpiresAt = now + CACHE_MILLIS;
        return cachedProvincias;
    }

    @Transactional
    public void crear(ProvinciaDTO dto) {
        String nombre = dto.getNombre() == null ? "" : dto.getNombre().trim();
        if (nombre.isEmpty()) throw new IllegalArgumentException("El nombre es obligatorio");
        repo.create(nombre);
        cacheExpiresAt = 0;
    }

    @Transactional
    public void actualizar(Long id, ProvinciaDTO dto) {
        String nombre = dto.getNombre();
        // si viene vacío → pasa null; tu SP hace COALESCE
        String n = (nombre == null || nombre.trim().isEmpty()) ? null : nombre.trim();
        repo.update(id, n);
        cacheExpiresAt = 0;
    }

    @Transactional
    public void eliminar(Long id) {
        repo.disable(id);
        cacheExpiresAt = 0;
    }
}
