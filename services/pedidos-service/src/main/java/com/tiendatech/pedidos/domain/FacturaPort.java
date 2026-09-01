package com.tiendatech.pedidos.domain;

import java.util.List;

public interface FacturaPort {
    Integer generarFactura(Orden orden, List<DetalleOrden> detalle);
}
