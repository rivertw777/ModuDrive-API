package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.exception.StorageExceptionCase;

import java.io.ByteArrayOutputStream;
import java.util.List;

/** Shared by the authenticated and the link-token download paths so the two can never drift. */
final class BlockAssembler {

    // ponytail: fixed cap; make configurable if a real need to tune it shows up.
    private static final long MAX_INLINE_PREVIEW_BYTES = 100L * 1024 * 1024;

    private BlockAssembler() {
    }

    static byte[] assemble(List<byte[]> blocks) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        // writeBytes, not write: it is the non-throwing overload, so there is no IOException to
        // swallow into a RuntimeException on an in-memory stream that cannot fail anyway.
        blocks.forEach(bos::writeBytes);
        return bos.toByteArray();
    }

    /** Regular download has no size limit, but inline preview does both a full assemble and a
     * Range slice of the (already fully in-memory) result — on a route the gateway now permits
     * without auth. blockCount * blockSize overestimates the true size (the last block is
     * usually smaller), which only makes this check more conservative, never less safe. Checked
     * before the S3 round trip so an oversized file is rejected without fetching it at all.
     * Callers only invoke this for {@code inlinePreview} commands — never call it unconditionally
     * for a regular download. */
    static void requireWithinInlinePreviewLimit(int blockCount, int blockSizeBytes) {
        if ((long) blockCount * blockSizeBytes > MAX_INLINE_PREVIEW_BYTES) {
            throw new BusinessException(StorageExceptionCase.PREVIEW_TOO_LARGE);
        }
    }
}
