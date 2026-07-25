package org.example.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.model.DetalleOrdenCompra;
import org.example.model.EstadoOrdenCompra;
import org.example.model.OrdenCompra;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public class OrdenCompraRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OrdenCompraRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public Integer crear(Integer proveedorId, Integer usuarioId, LocalDate fechaEsperada,
                         List<DetalleOrdenCompra> detalle) {
        String sql = "{call ordenes_proveedores.sp_crear_orden_compra_json(?, ?, ?, ?, ?)}";
        try {
            PGobject detalleJson = buildDetalleJson(detalle);
            return jdbcTemplate.execute((Connection con) -> {
                try (CallableStatement cs = con.prepareCall(sql)) {
                    cs.setInt(1, proveedorId);
                    cs.setInt(2, usuarioId);
                    cs.setDate(3, fechaEsperada != null ? Date.valueOf(fechaEsperada) : null);
                    cs.setObject(4, detalleJson);
                    cs.registerOutParameter(5, Types.INTEGER);
                    cs.execute();
                    return cs.getInt(5);
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Error creando orden de compra", e);
        }
    }

    public void actualizar(Integer ordenCompraId, Integer proveedorId, LocalDate fechaEsperada,
                           List<DetalleOrdenCompra> detalle) {
        String sql = "{call ordenes_proveedores.sp_actualizar_orden_compra_json(?, ?, ?, ?)}";
        try {
            PGobject detalleJson = buildDetalleJson(detalle);
            jdbcTemplate.execute((Connection con) -> {
                try (CallableStatement cs = con.prepareCall(sql)) {
                    cs.setInt(1, ordenCompraId);
                    cs.setInt(2, proveedorId);
                    cs.setDate(3, fechaEsperada != null ? Date.valueOf(fechaEsperada) : null);
                    cs.setObject(4, detalleJson);
                    cs.execute();
                    return null;
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Error actualizando orden de compra " + ordenCompraId, e);
        }
    }

    public void enviar(Integer ordenCompraId) {
        llamarProcedimientoSimple("{call ordenes_proveedores.sp_enviar_orden_compra(?)}", ordenCompraId);
    }

    public void cancelar(Integer ordenCompraId) {
        llamarProcedimientoSimple("{call ordenes_proveedores.sp_cancelar_orden_compra(?)}", ordenCompraId);
    }

    // p_recepcion: producto_id -> cantidad que llega ahora (se acumula sobre cantidad_recibida)
    public void registrarRecepcion(Integer ordenCompraId, Map<Integer, Integer> recepcionPorProducto) {
        String sql = "{call ordenes_proveedores.sp_registrar_recepcion_json(?, ?)}";
        try {
            ArrayNode array = objectMapper.createArrayNode();
            for (Map.Entry<Integer, Integer> entry : recepcionPorProducto.entrySet()) {
                ObjectNode node = array.addObject();
                node.put("producto_id", entry.getKey());
                node.put("cantidad", entry.getValue());
            }
            PGobject detalleJson = new PGobject();
            detalleJson.setType("jsonb");
            detalleJson.setValue(objectMapper.writeValueAsString(array));

            jdbcTemplate.execute((Connection con) -> {
                try (CallableStatement cs = con.prepareCall(sql)) {
                    cs.setInt(1, ordenCompraId);
                    cs.setObject(2, detalleJson);
                    cs.execute();
                    return null;
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Error registrando recepcion de orden " + ordenCompraId, e);
        }
    }

    public List<OrdenCompra> listarPorEstado(EstadoOrdenCompra estado) {
        String json;
        if (estado == null) {
            json = jdbcTemplate.queryForObject(
                    "SELECT ordenes_proveedores.fn_listar_ordenes_por_estado(NULL)::text", String.class);
        } else {
            json = jdbcTemplate.queryForObject(
                    "SELECT ordenes_proveedores.fn_listar_ordenes_por_estado(?::ordenes_proveedores.estado_orden_compra)::text",
                    String.class, estado.name());
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<OrdenCompra>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo respuesta de fn_listar_ordenes_por_estado", e);
        }
    }

    public OrdenCompra obtenerPorId(Integer ordenCompraId) {
        String sql = "SELECT ordenes_proveedores.fn_obtener_orden_compra(?)::text";
        String json = jdbcTemplate.queryForObject(sql, String.class, ordenCompraId);
        if (json == null || "null".equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OrdenCompra.class);
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo respuesta de fn_obtener_orden_compra", e);
        }
    }

    private void llamarProcedimientoSimple(String sql, Integer ordenCompraId) {
        jdbcTemplate.execute((Connection con) -> {
            try (CallableStatement cs = con.prepareCall(sql)) {
                cs.setInt(1, ordenCompraId);
                cs.execute();
                return null;
            }
        });
    }

    // Claves en snake_case porque asi las lee sp_crear_orden_compra_json / sp_actualizar_orden_compra_json
    // (v_linea->>'producto_id', etc). Independiente de como se serialice DetalleOrdenCompra hacia afuera.
    private PGobject buildDetalleJson(List<DetalleOrdenCompra> detalle) throws Exception {
        if (detalle == null || detalle.isEmpty()) {
            throw new IllegalArgumentException("La orden de compra debe tener al menos una linea de detalle");
        }
        ArrayNode array = objectMapper.createArrayNode();
        for (DetalleOrdenCompra d : detalle) {
            ObjectNode node = array.addObject();
            node.put("producto_id", d.getProductoId());
            node.put("cantidad_pedida", d.getCantidadPedida());
            node.put("costo_unitario", d.getCostoUnitario());
        }
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(objectMapper.writeValueAsString(array));
        return jsonb;
    }
}