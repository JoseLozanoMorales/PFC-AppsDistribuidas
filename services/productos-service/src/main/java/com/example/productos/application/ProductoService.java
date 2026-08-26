package com.example.productos.application;

import com.example.productos.domain.ImagenProducto;
import com.example.productos.domain.MediaProducto;
import com.example.productos.domain.ProductoRepository;
import com.example.productos.domain.ProductoResumen;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Fachada de casos de uso; toda persistencia se delega al puerto del dominio. */
@Service
public class ProductoService {
    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
    }

    public List<ProductoResumen> listar(int page, int size) { return repository.listar(page, size); }
    public List<Map<String, Object>> masVendidos(int limite) { return repository.masVendidos(limite); }
    public List<Map<String, Object>> recientesMenu(int limit) { return repository.recientesMenu(limit); }
    public List<Map<String, Object>> categorias() { return repository.categorias(); }
    public List<Map<String, Object>> marcas() { return repository.marcas(); }
    public List<Map<String, Object>> gamas() { return repository.gamas(); }
    public MediaProducto galeriaContenido(Integer galeriaId) { return repository.galeriaContenido(galeriaId); }
    public List<Map<String, Object>> galeriaProducto(Integer productoId, String scope) { return repository.galeriaProducto(productoId, scope); }
    public List<Map<String, Object>> buscar(Map<String, Object> filtros) { return repository.buscar(filtros); }
    public List<Map<String, Object>> porCategoria(Integer categoriaId, int page, int size) { return repository.porCategoria(categoriaId, page, size); }
    public Map<String, Object> detalle(Integer id) { return repository.detalle(id); }
    public Long crear(Integer categoriaId, Map<String, Object> body, String usuario) { return repository.crear(categoriaId, body, usuario); }
    public Long agregarImagen(Integer productoId, ImagenProducto imagen, String descripcion, boolean portada) { return repository.agregarImagen(productoId, imagen, descripcion, portada); }
    public void quitarImagen(Integer galeriaId) { repository.quitarImagen(galeriaId); }
    public void ordenarGaleria(Integer productoId, List<Integer> ids) { repository.ordenarGaleria(productoId, ids); }
    public void eliminar(Integer productoId, String usuario) { repository.eliminar(productoId, usuario); }
    public List<Map<String, Object>> detalleParaEditar(Integer id) { return repository.detalleParaEditar(id); }
    public List<Map<String, Object>> listarIvas() { return repository.listarIvas(); }
    public void actualizarBasico(Integer productoId, Map<String, Object> body, String usuario) { repository.actualizarBasico(productoId, body, usuario); }
    public void activar(Integer productoId) { repository.activar(productoId); }
}
