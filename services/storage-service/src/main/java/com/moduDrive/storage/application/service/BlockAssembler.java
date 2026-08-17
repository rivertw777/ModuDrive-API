package com.moduDrive.storage.application.service;

import java.io.ByteArrayOutputStream;
import java.util.List;

/** Shared by the authenticated and the link-token download paths so the two can never drift. */
final class BlockAssembler {

    private BlockAssembler() {
    }

    static byte[] assemble(List<byte[]> blocks) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        // writeBytes, not write: it is the non-throwing overload, so there is no IOException to
        // swallow into a RuntimeException on an in-memory stream that cannot fail anyway.
        blocks.forEach(bos::writeBytes);
        return bos.toByteArray();
    }
}
