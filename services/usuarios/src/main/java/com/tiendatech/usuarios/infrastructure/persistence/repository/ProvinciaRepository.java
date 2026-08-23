package com.tiendatech.usuarios.infrastructure.persistence.repository;

import com.tiendatech.usuarios.infrastructure.persistence.entity.Provincia;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProvinciaRepository extends JpaRepository<Provincia, Short> {

    @Modifying
    @Transactional
    @Query(value =
            "INSERT INTO usuarios.provincia (nombre,habilitado) VALUES (:nombre,true)", nativeQuery = true)
    void agregar(@Param("nombre") String nombre);

    @Modifying
    @Transactional
    @Query(value =
            "UPDATE usuarios.provincia SET nombre=coalesce(:nombre,nombre) WHERE provincia_id=:id", nativeQuery = true)
    void editar(@Param("id") Long id, @Param("nombre") String nombreNullable);

    @Modifying
    @Transactional
    @Query(value =
            "UPDATE usuarios.provincia SET habilitado=false WHERE provincia_id=:id", nativeQuery = true)
    void eliminar(@Param("id") Long id);
}
