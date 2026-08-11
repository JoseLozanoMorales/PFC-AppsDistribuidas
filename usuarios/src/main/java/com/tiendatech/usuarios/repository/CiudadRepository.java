// src/main/java/com/example/tienda_tech/repository/CiudadRepository.java
package com.tiendatech.usuarios.repository;

import com.tiendatech.usuarios.model.Ciudad;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CiudadRepository extends JpaRepository<Ciudad, Short> {

    @Modifying @Transactional
    @Query(value =
            "INSERT INTO usuarios.ciudad (nombre,provincia_id,habilitado) VALUES (:nombre,:provinciaId,true)", nativeQuery = true)
    void agregar(@Param("nombre") String nombre, @Param("provinciaId") Short provinciaId);

    @Modifying @Transactional
    @Query(value =
            "UPDATE usuarios.ciudad SET nombre=coalesce(:nombre,nombre),provincia_id=:provinciaId WHERE ciudad_id=:id", nativeQuery = true)
    void editar(@Param("id") Short id,
                @Param("nombre") String nombreNullable,   // puede ir null para que el SP haga COALESCE
                @Param("provinciaId") Short provinciaId); // <-- obligatorio

    @Modifying @Transactional
    @Query(value =
            "UPDATE usuarios.ciudad SET habilitado=false WHERE ciudad_id=:id", nativeQuery = true)
    void eliminar(@Param("id") Short id);
}
