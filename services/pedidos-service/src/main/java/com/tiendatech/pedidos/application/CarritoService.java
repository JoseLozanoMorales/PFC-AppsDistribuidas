package com.tiendatech.pedidos.application;

import com.tiendatech.pedidos.domain.Carrito;
import com.tiendatech.pedidos.domain.CarritoDetalle;
import com.tiendatech.pedidos.domain.CarritoRepository;
import com.tiendatech.pedidos.domain.PageResponse;
import com.tiendatech.pedidos.domain.Paginacion;
import com.tiendatech.pedidos.domain.ProductoPort;
import com.tiendatech.pedidos.domain.ProductoInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CarritoService {

    private final CarritoRepository carritoRepository;
    private final ProductoPort productoClient;

    @Autowired
    public CarritoService(CarritoRepository carritoRepository, ProductoPort productoClient) {
        this.carritoRepository = carritoRepository;
        this.productoClient = productoClient;
    }

    public Carrito obtenerCarritoActivo(Integer usuarioId) {
        return carritoRepository.obtenerActivo(usuarioId);
    }

    public Carrito obtenerCarritoPorId(Integer carritoId) {
        return carritoRepository.obtenerPorId(carritoId);
    }

    public Carrito crearCarrito(Integer usuarioId) {
        return carritoRepository.crear(usuarioId);
    }

    public PageResponse<CarritoDetalle> listarDetalle(Integer carritoId, Paginacion paginacion) {
        return carritoRepository.listarDetalle(carritoId, paginacion);
    }

    // Ahora el precio NO viene del cliente -- se consulta a productos-service
    // para evitar que alguien manipule el precio desde el frontend.
    public void agregarProducto(Integer carritoId, Integer productoId, Integer cantidad) {
        ProductoInfo info = productoClient.obtenerPrecioEIva(productoId);
        carritoRepository.agregarProducto(carritoId, productoId, cantidad, info.precioUnitario());
    }

    public void quitarProducto(Integer carritoId, Integer productoId) {
        carritoRepository.quitarProducto(carritoId, productoId);
    }

    public void actualizarCantidad(Integer carritoId, Integer productoId, Integer cantidad) {
        carritoRepository.actualizarCantidad(carritoId, productoId, cantidad);
    }
}
