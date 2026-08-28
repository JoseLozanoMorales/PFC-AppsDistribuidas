package com.tiendatech.pedidos.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiendatech.pedidos.domain.ReservationCommand;
import com.tiendatech.pedidos.domain.ReservationPort;
import com.tiendatech.pedidos.domain.ReservationResult;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@Component
public class LengthPrefixedTcpReservationClient implements ReservationPort {
    private static final int MAX_MESSAGE_BYTES = 64 * 1024;
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final ObjectMapper mapper;
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;

    public LengthPrefixedTcpReservationClient(@Value("${reservation.tcp.host:localhost}") String host,
            @Value("${reservation.tcp.port:9091}") int port,
            @Value("${reservation.tcp.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${reservation.tcp.read-timeout-ms:5000}") int readTimeoutMs,
            ObjectMapper mapper) {
        this.host = host; this.port = port; this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs; this.mapper = mapper;
    }

    @Override
    public synchronized ReservationResult reconcile(ReservationCommand command) {
        try {
            return exchange(command);
        } catch (IOException firstFailure) {
            close();
            try { return exchange(command); }
            catch (IOException retryFailure) {
                throw new IllegalStateException("Canal TCP de reservas no disponible", retryFailure);
            }
        }
    }

    private ReservationResult exchange(ReservationCommand command) throws IOException {
        ensureConnected();
        byte[] payload = mapper.writeValueAsBytes(command);
        output.writeInt(payload.length);
        output.write(payload);
        output.flush();
        int length = input.readInt();
        if (length <= 0 || length > MAX_MESSAGE_BYTES) throw new IOException("Respuesta TCP inválida: " + length);
        byte[] response = new byte[length];
        input.readFully(response);
        return mapper.readValue(response, ReservationResult.class);
    }

    private void ensureConnected() throws IOException {
        if (socket != null && socket.isConnected() && !socket.isClosed()) return;
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);
        socket.setKeepAlive(true);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
    }

    @PreDestroy
    public synchronized void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null; input = null; output = null;
    }
}
