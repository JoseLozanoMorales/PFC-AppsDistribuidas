package com.tiendatech.productos.domain;

import java.util.List;
import java.util.Map;

/** Puerto de salida que aisla los casos de uso de JDBC y CockroachDB. */
public interface ProductoRepository {
    List<ProductoResumen> listar(int page, int size);
    List<Map<String, Object>> resumirVentas(List<Map<String, Object>> ventas);
    List<Map<String, Object>> recientesMenu(int limit);
    List<Map<String, Object>> categorias();
    List<Map<String, Object>> marcas();
    List<Map<String, Object>> gamas();
    MediaProducto galeriaContenido(Integer galeriaId);
    List<Map<String, Object>> galeriaProducto(Integer productoId, String scope);
    List<Map<String, Object>> buscar(Map<String, Object> filtros);
    List<Map<String, Object>> porCategoria(Integer categoriaId, int page, int size);
    Map<String, Object> detalle(Integer id);
    Long crear(Integer categoriaId, Map<String, Object> body, String usuario);
    Long agregarImagen(Integer productoId, ImagenProducto imagen, String descripcion, boolean portada);
    void quitarImagen(Integer galeriaId);
    void ordenarGaleria(Integer productoId, List<Integer> ids);
    void eliminar(Integer productoId, String usuario);
    List<Map<String, Object>> detalleParaEditar(Integer id);
    List<Map<String, Object>> listarIvas();
    void actualizarBasico(Integer productoId, Map<String, Object> body, String usuario);
    void activar(Integer productoId);
}
