package com.moduDrive.storage.application.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class CountingOutputStreamTest {

    @Test
    void talliesEveryByteWrittenThroughIt() throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        CountingOutputStream counting = new CountingOutputStream(sink);

        counting.write('a');
        counting.write(new byte[] {1, 2, 3, 4}, 1, 2);
        counting.write(new byte[] {9, 9, 9});

        assertThat(counting.count()).isEqualTo(1 + 2 + 3);
        assertThat(sink.toByteArray()).containsExactly('a', 2, 3, 9, 9, 9);
    }

    @Test
    void chargesAWriteThatThrewPartwaySinceTheUpstreamFetchWasAlreadyPaid() {
        OutputStream failing = new OutputStream() {
            @Override public void write(int b) throws IOException { throw new IOException("client gone"); }
            @Override public void write(byte[] b, int off, int len) throws IOException { throw new IOException("client gone"); }
        };
        CountingOutputStream counting = new CountingOutputStream(failing);

        Throwable thrown = catchThrowable(() -> counting.write(new byte[4096], 0, 4096));

        assertThat(thrown).isInstanceOf(IOException.class);
        assertThat(counting.count()).isEqualTo(4096L);
    }
}
