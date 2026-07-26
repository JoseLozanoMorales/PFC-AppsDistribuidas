package org.example.service;

import org.example.model.DetalleOrdenCompra;
import org.example.model.EstadoOrdenCompra;
import org.example.model.OrdenCompra;
import org.example.repository.OrdenCompraRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class OrdenCompraService {

    private final OrdenCompraRepository ordenCompraRepository;

    public OrdenCompraService(OrdenCompraRepository ordenCompraRepository) {
        this.ordenCompraRepository = ordenCompraRepository;
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
    public void registrarRecepcion(Integer ordenCompraId, Map<Integer, Integer> recepcionPorProducto) {
        ordenCompraRepository.registrarRecepcion(ordenCompraId, recepcionPorProducto);
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