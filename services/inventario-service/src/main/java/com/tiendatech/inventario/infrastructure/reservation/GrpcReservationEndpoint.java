package com.tiendatech.inventario.infrastructure.reservation;

import com.tiendatech.contracts.reservas.v1.ReservationRequest;
import com.tiendatech.contracts.reservas.v1.ReservationResponse;
import com.tiendatech.contracts.reservas.v1.StockReservationServiceGrpc;
import com.tiendatech.inventario.application.reservation.StockReservationService;
import com.tiendatech.inventario.domain.reservation.ReservationCommand;
import com.tiendatech.inventario.domain.reservation.ReservationResult;
import io.grpc.stub.StreamObserver;

public class GrpcReservationEndpoint extends StockReservationServiceGrpc.StockReservationServiceImplBase {
    private final StockReservationService service;

    public GrpcReservationEndpoint(StockReservationService service) { this.service = service; }

    @Override
    public void reconcileReservation(ReservationRequest request, StreamObserver<ReservationResponse> observer) {
        try {
            ReservationResult result = service.reconcile(new ReservationCommand(request.getCartId(),
                    request.getUserId(), request.getProductId(), request.getQuantity(),
                    request.getLamportTimestamp(), request.getDeviceId(), request.getOperationId()));
            observer.onNext(ReservationResponse.newBuilder().setAccepted(result.accepted())
                    .setMessage(result.message()).setReservedQuantity(result.reservedQuantity())
                    .setAvailableStock(result.availableStock()).setLamportTimestamp(result.lamportTimestamp())
                    .setWinningDeviceId(result.winningDeviceId()).setReplayed(result.replayed()).build());
            observer.onCompleted();
        } catch (Exception error) {
            observer.onNext(ReservationResponse.newBuilder().setAccepted(false)
                    .setMessage(error.getMessage() == null ? "Error de reserva" : error.getMessage()).build());
            observer.onCompleted();
        }
    }
}
