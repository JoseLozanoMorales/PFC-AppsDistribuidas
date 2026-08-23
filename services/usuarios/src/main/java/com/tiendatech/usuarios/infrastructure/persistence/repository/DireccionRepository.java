package com.tiendatech.usuarios.infrastructure.persistence.repository;

import com.tiendatech.usuarios.infrastructure.persistence.entity.Direccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DireccionRepository extends JpaRepository<Direccion, Short> {

    // Listado solo habilitadas
    List<Direccion> findByUsuario_UsuarioIdAndHabilitadoTrue(Integer usuarioId);

    // === SP wrappers ===
    @Modifying
    @Transactional
    @Query(value = """
      INSERT INTO usuarios.direccion (calle,referencia,usuario_id,ciudad_id,habilitado)
      VALUES (:calle,:referencia,:usuarioId,:ciudadId,true)
      """, nativeQuery = true)
    void agregar(@Param("usuarioId") Integer usuarioId,
                 @Param("ciudadId")  Short ciudadId,
                 @Param("calle")     String calle,
                 @Param("referencia") String referenciaNullable);

    @Modifying
    @Transactional
    @Query(value = """
      UPDATE usuarios.direccion SET calle=coalesce(:calle,calle),
        referencia=coalesce(:referencia,referencia),usuario_id=:usuarioId,
        ciudad_id=:ciudadId WHERE direccion_id=:id
      """, nativeQuery = true)
    void editar(@Param("id")       Short direccionId,
                @Param("usuarioId") Integer usuarioId,
                @Param("ciudadId")  Short ciudadId,
                @Param("calle")     String calleNullable,
                @Param("referencia") String referenciaNullable);

    @Modifying
    @Transactional
    @Query(value = """
      UPDATE usuarios.direccion SET habilitado=false WHERE direccion_id=:id
      """, nativeQuery = true)
    void eliminar(@Param("id") Short direccionId);
}
