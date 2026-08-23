package org.example.application;

import org.example.domain.DetalleOrdenCompra;
import org.example.domain.EstadoOrdenCompra;
import org.example.domain.OrdenCompra;
import org.example.domain.OrdenCompraRepository;
import org.example.domain.InventarioPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class OrdenCompraService {

    private final OrdenCompraRepository ordenCompraRepository;
    private final InventarioPort inventarioClient;

    public OrdenCompraService(OrdenCompraRepository ordenCompraRepository,
                              InventarioPort inventarioClient) {
        this.ordenCompraRepository = ordenCompraRepository;
        this.inventarioClient = inventarioClient;
    }

    public Integer crear(Integer proveedorId, Integer usuarioId, LocalDate fechaEsperada,
                         List<DetalleOrdenCompra> detalle) {
        return ordenCompraRepository.crear(proveedorId, usuarioId, fechaEsperada, detalle);
    }

    public void actualizar(Integer ordenCompraId, Integer proveedorId, LocalDate fechaEsperada,
                           List<DetalleOrdenCompra> detalle) {
        ordenCompraRepository.actualizar(ordenCompraId, proveedorId, fechaEsperada, detalle);
    }

    public void enviar(Integer ordenCompraId) {
        ordenCompraRepository.enviar(ordenCompraId);
    }

    public void cancelar(Integer ordenCompraId) {
        ordenCompraRepository.cancelar(ordenCompraId);
    }

    // NOTA: esto solo confirma la recepcion en ordenes_proveedores. La actualizacion de
    // stock en inventario-service (que tu propio sp_registrar_recepcion_json deja explicita
    // como responsabilidad del backend Java) todavia no esta conectada aca -- la sumamos
    // como paso aparte mas adelante.
    public void registrarRecepcion(Integer ordenCompraId, Map<Integer, Integer> recepcionPorProducto, String usuario) {
        ordenCompraRepository.registrarRecepcion(ordenCompraId, recepcionPorProducto);
        try {
            inventarioClient.registrarEntradasPorRecepcion(ordenCompraId, recepcionPorProducto, usuario);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "La recepcion de la orden " + ordenCompraId + " quedo confirmada, pero fallo la "
                            + "actualizacion de stock en inventario-service. Revisar manualmente.", e);
        }
    }

    public List<OrdenCompra> listarPorEstado(EstadoOrdenCompra estado) {
        return ordenCompraRepository.listarPorEstado(estado);
    }

    public OrdenCompra obtenerPorId(Integer ordenCompraId) {
        OrdenCompra orden = ordenCompraRepository.obtenerPorId(ordenCompraId);
        if (orden == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden de compra " + ordenCompraId + " no existe");
        }
        return orden;
    }
}
