package com.moduDrive.storage.application.port.in.usecase;

import java.io.IOException;
import java.io.OutputStream;

public interface OpenArchiveUseCase {

    /** Redeems a token from {@link PrepareArchiveUseCase}. A pick of one file comes back as that file
     * itself, not zipped. Everything that can fail cleanly (bad
     * token, access revoked since) fails here, before any response byte is written. */
    Archive open(String token);

    /** {@code contentLength} is null when it isn't known up front (a zip). */
    record Archive(String fileName, String contentType, Long contentLength, Writer writer) {}

    @FunctionalInterface
    interface Writer {
        void writeTo(OutputStream out) throws IOException;
    }
}
