package com.tiendatech.inventario.presentation;

import com.tiendatech.inventario.application.reservation.LamportClock;
import com.tiendatech.inventario.infrastructure.reservation.GrpcReservationServer;
import com.tiendatech.inventario.infrastructure.reservation.TcpReservationServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/reservas")
public class ReservationStatusController {
    private final TcpReservationServer tcp;
    private final GrpcReservationServer grpc;
    private final LamportClock clock;
    private final int tcpPort;
    private final int grpcPort;

    public ReservationStatusController(TcpReservationServer tcp, GrpcReservationServer grpc, LamportClock clock,
            @Value("${reservation.tcp.port:9091}") int tcpPort,
            @Value("${reservation.grpc.port:9092}") int grpcPort) {
        this.tcp = tcp; this.grpc = grpc; this.clock = clock; this.tcpPort = tcpPort; this.grpcPort = grpcPort;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("tcp", tcp.isRunning(), "tcpPort", tcpPort, "grpc", grpc.isRunning(),
                "grpcPort", grpcPort, "lamport", clock.current(), "framing", "uint32-big-endian");
    }
}
