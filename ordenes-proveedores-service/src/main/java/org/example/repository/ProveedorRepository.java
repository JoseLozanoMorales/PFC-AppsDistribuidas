package org.example.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.core.type.TypeReference;
import org.example.model.Proveedor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.util.List;

@Repository
public class ProveedorRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ProveedorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        // Las funciones de BD devuelven columnas en snake_case (proveedor_id, contacto_nombre...);
        // este mapper es solo para leer esas respuestas, no afecta el JSON que expone el controller.
        this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public Integer crear(Proveedor p) {
        String sql = "call ordenes_proveedores.sp_crear_proveedor(?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall(sql)) {
                cs.setString(1, p.getNombre());
                cs.setString(2, p.getRuc());
                cs.setString(3, p.getContactoNombre());
                cs.setString(4, p.getTelefono());
                cs.setString(5, p.getCorreo());
                cs.setString(6, p.getDireccion());
                cs.registerOutParameter(7, Types.INTEGER);
                cs.execute();
                return cs.getInt(7);
            }
        });
    }

    public void actualizar(Proveedor p) {
        String sql = "call ordenes_proveedores.sp_actualizar_proveedor(?, ?, ?, ?, ?, ?)";
        jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall(sql)) {
                cs.setInt(1, p.getProveedorId());
                cs.setString(2, p.getNombre());
                cs.setString(3, p.getContactoNombre());
                cs.setString(4, p.getTelefono());
                cs.setString(5, p.getCorreo());
                cs.setString(6, p.getDireccion());
                cs.execute();
                return null;
            }
        });
    }

    public void desactivar(Integer proveedorId) {
        String sql = "call ordenes_proveedores.sp_desactivar_proveedor(?)";
        jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall(sql)) {
                cs.setInt(1, proveedorId);
                cs.execute();
                return null;
            }
        });
    }

    public List<Proveedor> listarActivos() {
        String sql = "SELECT ordenes_proveedores.fn_listar_proveedores_activos()::text";
        String json = jdbcTemplate.queryForObject(sql, String.class);
        try {
            return objectMapper.readValue(json, new TypeReference<List<Proveedor>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo respuesta de fn_listar_proveedores_activos", e);
        }
    }

    public Proveedor obtenerPorId(Integer proveedorId) {
        String sql = "SELECT ordenes_proveedores.fn_obtener_proveedor(?)::text";
        String json = jdbcTemplate.queryForObject(sql, String.class, proveedorId);
        if (json == null || "null".equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Proveedor.class);
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo respuesta de fn_obtener_proveedor", e);
        }
    }
}