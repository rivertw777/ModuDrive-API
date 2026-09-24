package com.moduDrive.storage.application.port.in.usecase;

import java.io.IOException;
import java.io.OutputStream;

public interface OpenArchiveUseCase {

    /** Redeems a token from {@link PrepareArchiveUseCase}. Everything that can fail cleanly (bad
     * token, access revoked since) fails here, before any response byte is written. */
    Archive open(String token);

    record Archive(String fileName, Writer writer) {}

    @FunctionalInterface
    interface Writer {
        void writeTo(OutputStream out) throws IOException;
    }
}
