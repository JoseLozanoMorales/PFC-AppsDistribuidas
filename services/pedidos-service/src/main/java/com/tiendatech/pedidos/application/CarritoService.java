package com.tiendatech.pedidos.application;

import com.tiendatech.pedidos.domain.Carrito;
import com.tiendatech.pedidos.domain.CarritoDetalle;
import com.tiendatech.pedidos.domain.CarritoRepository;
import com.tiendatech.pedidos.domain.PageResponse;
import com.tiendatech.pedidos.domain.Paginacion;
import com.tiendatech.pedidos.domain.ProductoPort;
import com.tiendatech.pedidos.domain.ProductoInfo;
import com.tiendatech.pedidos.domain.ReservationCommand;
import com.tiendatech.pedidos.domain.ReservationPort;
import com.tiendatech.pedidos.domain.ReservationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CarritoService {

    private final CarritoRepository carritoRepository;
    private final ProductoPort productoClient;
    private ReservationPort reservationClient;
    private CartLamportClock lamportClock;

    @Autowired
    public CarritoService(CarritoRepository carritoRepository, ProductoPort productoClient) {
        this.carritoRepository = carritoRepository;
        this.productoClient = productoClient;
    }

    @Autowired
    public void configureReservations(ReservationPort reservationClient, CartLamportClock lamportClock) {
        this.reservationClient = reservationClient;
        this.lamportClock = lamportClock;
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

    public ReservationResult agregarProducto(Integer carritoId, Integer usuarioId, Integer productoId,
            Integer cantidad, String deviceId, long remoteLamport, String operationId) {
        int current = currentQuantity(carritoId, productoId);
        ProductoInfo info = productoClient.obtenerPrecioEIva(productoId);
        ReservationResult result = reserve(carritoId, usuarioId, productoId, current + cantidad,
                deviceId, remoteLamport, operationId);
        reconcileDatabase(carritoId, productoId, result.reservedQuantity(), info);
        return result;
    }

    public ReservationResult actualizarCantidad(Integer carritoId, Integer usuarioId, Integer productoId,
            Integer quantity, String deviceId, long remoteLamport, String operationId) {
        ReservationResult result = reserve(carritoId, usuarioId, productoId, Math.max(0, quantity),
                deviceId, remoteLamport, operationId);
        ProductoInfo info = result.reservedQuantity() > 0 ? productoClient.obtenerPrecioEIva(productoId) : null;
        reconcileDatabase(carritoId, productoId, result.reservedQuantity(), info);
        return result;
    }

    private ReservationResult reserve(int cartId, int userId, int productId, int quantity,
            String deviceId, long remoteLamport, String operationId) {
        if (reservationClient == null || lamportClock == null) {
            throw new IllegalStateException("Canal de reservas no configurado");
        }
        long outgoing = lamportClock.receive(cartId, remoteLamport);
        ReservationResult result = reservationClient.reconcile(new ReservationCommand(cartId, userId,
                productId, quantity, outgoing, deviceId, operationId));
        lamportClock.receiveResponse(cartId, result.lamportTimestamp());
        return result;
    }

    private int currentQuantity(int cartId, int productId) {
        return carritoRepository.listarDetalle(cartId, Paginacion.de(0, 100)).content().stream()
                .filter(line -> line.getProductoId().equals(productId)).mapToInt(CarritoDetalle::getCantidad)
                .findFirst().orElse(0);
    }

    private void reconcileDatabase(int cartId, int productId, int quantity, ProductoInfo info) {
        int current = currentQuantity(cartId, productId);
        if (quantity <= 0) carritoRepository.quitarProducto(cartId, productId);
        else if (current == 0) carritoRepository.agregarProducto(cartId, productId, quantity, info.precioUnitario());
        else carritoRepository.actualizarCantidad(cartId, productId, quantity);
    }

    public CartReservationSnapshot snapshotForCheckout(int userId) {
        Carrito cart = carritoRepository.obtenerActivo(userId);
        if (cart == null) return null;
        List<Integer> products = carritoRepository.listarDetalle(cart.getCarritoId(), Paginacion.de(0, 100))
                .content().stream().map(CarritoDetalle::getProductoId).toList();
        return new CartReservationSnapshot(cart.getCarritoId(), userId, products);
    }

    public void releaseAfterCheckout(CartReservationSnapshot snapshot) {
        if (snapshot == null) return;
        for (Integer productId : snapshot.productIds()) {
            try {
                reserve(snapshot.cartId(), snapshot.userId(), productId, 0, "checkout", 0,
                        UUID.randomUUID().toString());
            } catch (RuntimeException ignored) {
                // La venta ya fue confirmada: una limpieza fallida se conserva para reintento operativo.
            }
        }
    }

    public record CartReservationSnapshot(int cartId, int userId, List<Integer> productIds) {}
}
