package com.tiendatech.ordenesproveedores.domain;

import java.util.List;

/**
 * Puerto de dominio (patron Repository): define el contrato de persistencia de
 * Proveedor sin acoplarse a JDBC/Spring. La implementacion real vive en
 * infrastructure.persistence.JdbcProveedorRepository.
 */
public interface ProveedorRepository {

    Integer crear(Proveedor proveedor);

    void actualizar(Proveedor proveedor);

    void desactivar(Integer proveedorId);

    void activar(Integer proveedorId);

    // Devuelve todos los proveedores (activos e inactivos): la lista de administracion
    // de proveedores debe seguir mostrando los desactivados, solo que marcados como
    // "Inactivo". El filtro a solo-activos se hace en el select de creacion de ordenes.
    List<Proveedor> listarTodos();

    Proveedor obtenerPorId(Integer proveedorId);
}
