package com.moduDrive.storage.application.service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Counts the bytes written through it, so a download is metered by what was actually sent. Not
 * thread-safe: {@code StreamingResponseBody} writes on one thread. */
final class CountingOutputStream extends FilterOutputStream {

    private long count;

    CountingOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        try {
            out.write(b);
        } finally {
            count++;
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        // FilterOutputStream's default writes byte by byte; delegate directly. Counted in finally: a
        // write that throws partway still cost the fetch and the wire.
        try {
            out.write(b, off, len);
        } finally {
            count += len;
        }
    }

    long count() {
        return count;
    }
}
