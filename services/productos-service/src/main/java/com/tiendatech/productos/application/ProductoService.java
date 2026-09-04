package com.tiendatech.productos.application;

import com.tiendatech.productos.domain.ImagenProducto;
import com.tiendatech.productos.domain.MediaProducto;
import com.tiendatech.productos.domain.ProductoRepository;
import com.tiendatech.productos.domain.ProductoResumen;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Fachada de casos de uso; toda persistencia se delega al puerto del dominio. */
@Service
public class ProductoService {
    private static final long CATALOG_CACHE_MILLIS = 60_000;
    private final ProductoRepository repository;
    private final com.tiendatech.productos.domain.VentasPort ventas;
    private final Map<String, CacheEntry<?>> catalogCache = new ConcurrentHashMap<>();

    public ProductoService(ProductoRepository repository, com.tiendatech.productos.domain.VentasPort ventas) {
        this.repository = repository;
        this.ventas = ventas;
    }

    public List<ProductoResumen> listar(int page, int size) {
        return cached("productos:" + page + ':' + size, () -> repository.listar(page, size));
    }
    public List<Map<String, Object>> masVendidos(int limite) {
        return repository.resumirVentas(ventas.masVendidos(Math.max(limite, 1)));
    }
    public List<Map<String, Object>> recientesMenu(int limit) { return repository.recientesMenu(limit); }
    public List<Map<String, Object>> categorias() { return cached("categorias", repository::categorias); }
    public List<Map<String, Object>> marcas() { return cached("marcas", repository::marcas); }
    public List<Map<String, Object>> gamas() { return cached("gamas", repository::gamas); }
    public MediaProducto galeriaContenido(Integer galeriaId) { return repository.galeriaContenido(galeriaId); }
    public List<Map<String, Object>> galeriaProducto(Integer productoId, String scope) { return repository.galeriaProducto(productoId, scope); }
    public List<Map<String, Object>> buscar(Map<String, Object> filtros) { return repository.buscar(filtros); }
    public List<Map<String, Object>> porCategoria(Integer categoriaId, int page, int size) { return repository.porCategoria(categoriaId, page, size); }
    public Map<String, Object> detalle(Integer id) { return repository.detalle(id); }
    public Long crear(Integer categoriaId, Map<String, Object> body, String usuario) {
        Long id = repository.crear(categoriaId, body, usuario);
        catalogCache.clear();
        return id;
    }
    public Long agregarImagen(Integer productoId, ImagenProducto imagen, String descripcion, boolean portada) { return repository.agregarImagen(productoId, imagen, descripcion, portada); }
    public void quitarImagen(Integer galeriaId) { repository.quitarImagen(galeriaId); }
    public void ordenarGaleria(Integer productoId, List<Integer> ids) { repository.ordenarGaleria(productoId, ids); }
    public void eliminar(Integer productoId, String usuario) { repository.eliminar(productoId, usuario); catalogCache.clear(); }
    public List<Map<String, Object>> detalleParaEditar(Integer id) { return repository.detalleParaEditar(id); }
    public List<Map<String, Object>> listarIvas() { return repository.listarIvas(); }
    public void actualizarBasico(Integer productoId, Map<String, Object> body, String usuario) { repository.actualizarBasico(productoId, body, usuario); catalogCache.clear(); }
    public void activar(Integer productoId) { repository.activar(productoId); catalogCache.clear(); }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry<?> current = catalogCache.get(key);
        if (current != null && current.expiresAt() > now) return (T) current.value();
        return (T) catalogCache.compute(key, (ignored, existing) -> {
            long checkedAt = System.currentTimeMillis();
            if (existing != null && existing.expiresAt() > checkedAt) return existing;
            return new CacheEntry<>(loader.get(), checkedAt + CATALOG_CACHE_MILLIS);
        }).value();
    }

    private record CacheEntry<T>(T value, long expiresAt) {}
}
