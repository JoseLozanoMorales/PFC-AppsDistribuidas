package com.tiendatech.usuarios.repository.audit;

import com.tiendatech.usuarios.model.UsuarioAuditoria;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface UsuarioAuditoriaRepository extends JpaRepository<UsuarioAuditoria, Integer> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO usuarios.usuario_auditoria (usuario_id,ip,nombre_ordenador,fecha_login,cerrada) VALUES (:p_usuario_id,'127.0.0.1','usuarios-service',now(),false)", nativeQuery = true)
    void auditarLogin(@Param("p_usuario_id") Integer usuarioId);

    // ======== NUEVO: lectura por FUNCIONES (no @Modifying) ========
    @Query(value = "select distinct u.usuario from usuarios.usuario_auditoria a join usuarios.usuario u on u.usuario_id=a.usuario_id order by u.usuario", nativeQuery = true)
    List<String> fnUsuarios(); // llena el <select id="au_usuario">

    @Query(value = "select a.id_sesion,a.usuario_id,u.usuario,a.ip,a.nombre_ordenador,a.fecha_login from usuarios.usuario_auditoria a join usuarios.usuario u on u.usuario_id=a.usuario_id where (:usuario='' or lower(u.usuario) like '%'||lower(:usuario)||'%') and (:desde is null or a.fecha_login >= :desde) and (:hasta is null or a.fecha_login < (:hasta + interval '1 day')) order by a.fecha_login desc", nativeQuery = true)
    List<Object[]> fnLoginsRaw(
            @Param("usuario") String usuario,
            @Param("desde")   LocalDate desde,
            @Param("hasta")   LocalDate hasta   // día calendario; la función ya usa < (hasta+1)
    );
}
