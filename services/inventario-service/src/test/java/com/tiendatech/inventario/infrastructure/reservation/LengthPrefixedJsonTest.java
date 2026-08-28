package com.tiendatech.inventario.infrastructure.reservation;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LengthPrefixedJsonTest {
    @Test
    void readCompletesAFrameEvenWhenTheStreamReturnsOneByteAtATime() throws Exception {
        byte[] json = "{\"quantity\":2}".getBytes(StandardCharsets.UTF_8);
        byte[] frame = ByteBuffer.allocate(4 + json.length).putInt(json.length).put(json).array();
        InputStream fragmented = new InputStream() {
            int position;
            @Override public int read() { return position == frame.length ? -1 : frame[position++] & 0xff; }
            @Override public int read(byte[] target, int offset, int length) {
                if (position == frame.length) return -1;
                target[offset] = frame[position++];
                return 1;
            }
        };

        assertThat(LengthPrefixedJson.read(new DataInputStream(fragmented))).isEqualTo(json);
    }
}
