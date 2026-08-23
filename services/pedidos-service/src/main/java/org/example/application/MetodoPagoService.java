package org.example.application;

import org.example.domain.MetodoPago;
import org.example.domain.MetodoPagoRepository;
import org.example.domain.PageResponse;
import org.example.domain.Paginacion;
import org.example.domain.TipoMetodoPago;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MetodoPagoService {

    private final MetodoPagoRepository metodoPagoRepository;

    @Autowired
    public MetodoPagoService(MetodoPagoRepository metodoPagoRepository) {
        this.metodoPagoRepository = metodoPagoRepository;
    }

    public PageResponse<MetodoPago> listarPorUsuario(Integer usuarioId, Paginacion paginacion) {
        return metodoPagoRepository.listarPorUsuario(usuarioId, paginacion);
    }

    public MetodoPago obtenerPorIdYUsuario(Integer metodopagoId, Integer usuarioId) {
        return metodoPagoRepository.obtenerPorIdYUsuario(metodopagoId, usuarioId);
    }

    public List<TipoMetodoPago> listarTipos() {
        return metodoPagoRepository.listarTipos();
    }

    public Integer agregar(String numeroTarjeta, LocalDate fechaExpiracion, Integer tipoId, Integer usuarioId) {
        return metodoPagoRepository.agregar(numeroTarjeta, fechaExpiracion, tipoId, usuarioId);
    }

    public void actualizar(Integer metodopagoId, Integer usuarioId, String numeroTarjeta,
                           LocalDate fechaExpiracion, Integer tipoId, Boolean habilitado) {
        metodoPagoRepository.actualizar(metodopagoId, usuarioId, numeroTarjeta, fechaExpiracion, tipoId, habilitado);
    }

    public void inactivar(Integer metodopagoId, Integer usuarioId) {
        metodoPagoRepository.inactivar(metodopagoId, usuarioId);
    }

    public void reactivar(Integer metodopagoId, Integer usuarioId) {
        metodoPagoRepository.reactivar(metodopagoId, usuarioId);
    }
}
