package com.tiendatech.usuarios.repository;

import com.tiendatech.usuarios.model.Provincia;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProvinciaRepository extends JpaRepository<Provincia, Short> {

    @Modifying
    @Transactional
    @Query(value =
            "CALL usuarios.sp_procesar_provincias(" +
                    "  jsonb_build_array(jsonb_build_object('Accion','agregar','Nombre', :nombre))" +
                    ")", nativeQuery = true)
    void agregar(@Param("nombre") String nombre);

    @Modifying
    @Transactional
    @Query(value =
            "CALL usuarios.sp_procesar_provincias(" +
                    "  jsonb_build_array(jsonb_build_object('Accion','editar','ProvinciaId', :id, 'Nombre', :nombre))" +
                    ")", nativeQuery = true)
    void editar(@Param("id") Long id, @Param("nombre") String nombreNullable);

    @Modifying
    @Transactional
    @Query(value =
            "CALL usuarios.sp_procesar_provincias(" +
                    "  jsonb_build_array(jsonb_build_object('Accion','eliminar','ProvinciaId', :id))" +
                    ")", nativeQuery = true)
    void eliminar(@Param("id") Long id);
}
