package com.moduDrive.storage.application.service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Wraps a stream and tallies the bytes written through it, so a download can be metered by what
 * actually reached the client rather than by the file's nominal size — an aborted transfer then
 * only spends what really left. Not thread-safe: {@code StreamingResponseBody} runs the whole
 * write on one thread, which is the only context this is used in. */
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
        // FilterOutputStream's default fans this out to write(int) per byte; delegate straight
        // through instead, both for speed and so the count stays exact. Count in a finally: a
        // write that throws partway still cost us the upstream fetch and the wire, so charge it.
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
