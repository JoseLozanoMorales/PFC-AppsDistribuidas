package com.example.productos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductoServiceH10Test {

    private JdbcTemplate jdbc;
    private ProductoService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new ProductoService(jdbc, new ObjectMapper());
    }

    @Test
    void masVendidosUsaConsultaVersionadaEnLugarDeFuncionDeBaseDeDatos() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.masVendidos(5);

        String sql = capturarSqlConArgumentos();
        assertThat(sql)
                .doesNotContainIgnoringCase("productos_mas_vendidos_menu")
                .containsIgnoringCase("ventas.factura_cuerpo")
                .containsIgnoringCase("productos.producto");
    }

    @Test
    void recientesUsaConsultaVersionadaEnLugarDeFuncionDeBaseDeDatos() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.recientesMenu(5);

        String sql = capturarSqlConArgumentos();
        assertThat(sql)
                .doesNotContainIgnoringCase("f_productos_recientes_con_imagen_menu")
                .containsIgnoringCase("productos.producto")
                .containsIgnoringCase("productos.galeria_productos_v2");
    }

    @Test
    void categoriasUsaConsultaVersionadaEnLugarDeFuncionDeBaseDeDatos() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("id_categoria", 3, "nombre", "Tarjetas graficas")));

        List<Map<String, Object>> resultado = service.categorias();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue())
                .doesNotContainIgnoringCase("fn_listar_categorias")
                .containsIgnoringCase("productos.categoria_producto");
        assertThat(resultado.get(0))
                .containsEntry("id", 3)
                .containsEntry("slug", "tarjetas-graficas");
    }

    private String capturarSqlConArgumentos() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        return sql.getValue();
    }
}
