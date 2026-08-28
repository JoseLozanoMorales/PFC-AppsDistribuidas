package com.tiendatech.inventario.infrastructure.reservation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

public final class LengthPrefixedJson {
    public static final int MAX_MESSAGE_BYTES = 64 * 1024;

    private LengthPrefixedJson() {}

    public static byte[] read(DataInputStream input) throws IOException {
        int length;
        try {
            length = input.readInt();
        } catch (EOFException closed) {
            return null;
        }
        if (length <= 0 || length > MAX_MESSAGE_BYTES) {
            throw new IOException("Longitud de mensaje inválida: " + length);
        }
        byte[] payload = new byte[length];
        input.readFully(payload);
        return payload;
    }

    public static void write(DataOutputStream output, byte[] payload) throws IOException {
        if (payload.length == 0 || payload.length > MAX_MESSAGE_BYTES) {
            throw new IOException("Longitud de mensaje inválida: " + payload.length);
        }
        output.writeInt(payload.length);
        output.write(payload);
        output.flush();
    }
}
