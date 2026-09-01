package com.tiendatech.productos.infrastructure.persistence;

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

class JdbcProductoRepositoryH10Test {

    private JdbcTemplate jdbc;
    private JdbcProductoRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = new JdbcProductoRepository(jdbc, new ObjectMapper());
    }

    @Test
    void masVendidosEnriqueceRankingSinConsultarElEsquemaVentas() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("productoId", 7L, "nombre", "CPU", "precio", 100)));

        var resultado = repository.resumirVentas(List.of(
                Map.of("productoId", 7L, "unidades", 4)));

        String sql = capturarSqlConArgumentos();
        assertThat(sql)
                .doesNotContainIgnoringCase("ventas.")
                .containsIgnoringCase("productos.producto");
        assertThat(resultado.getFirst()).containsEntry("unidades", 4);
    }

    @Test
    void recientesUsaConsultaVersionadaEnLugarDeFuncionDeBaseDeDatos() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        repository.recientesMenu(5);

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

        List<Map<String, Object>> resultado = repository.categorias();

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
