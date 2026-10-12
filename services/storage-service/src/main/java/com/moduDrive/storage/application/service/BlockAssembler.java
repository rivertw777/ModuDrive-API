package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.usecase.RangeContent;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort.VersionLocation;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.exception.StorageExceptionCase;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
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

    /** Regular download has no size limit, but an inline preview without a Range (see
     * {@link #readRange} for the ranged one) assembles the whole file in memory — on a route the
     * gateway now permits without auth. blockCount * blockSize overestimates the true size (the last block is
     * usually smaller), which only makes this check more conservative, never less safe. Checked
     * before the S3 round trip so an oversized file is rejected without fetching it at all.
     * Callers only invoke this for {@code inlinePreview} commands — never call it unconditionally
     * for a regular download. */
    static void requireWithinInlinePreviewLimit(int blockCount, int blockSizeBytes) {
        if ((long) blockCount * blockSizeBytes > MAX_INLINE_PREVIEW_BYTES) {
            throw new BusinessException(StorageExceptionCase.PREVIEW_TOO_LARGE);
        }
    }

    /** A {@code Range} request reads only the blocks covering it instead of the whole file — a
     * {@code <video>} sends one per seek (plus an open-ended {@code bytes=0-} to start), so
     * assembling the whole file for each held up to 100MB per request and OOM-killed the task
     * (issue #634). The slice is capped at one block's worth of bytes (at most two blocks read),
     * which RFC 9110 allows and media elements handle by asking for the next range. Uploads cut
     * every block but the last at exactly {@code blockSize} (file-service's commit check), so
     * byte offsets map straight to block indices. A start past the end — a stale Range, or a
     * version with no recorded size — falls back to the whole file, under the preview cap. */
    static RangeContent readRange(RetrieveBlocksPort retrieveBlocksPort, DownloadQuotaPort downloadQuotaPort,
                                  String scope, VersionLocation version, int blockSize, long start, Long end) {
        long total = version.fileSize();
        if (start >= total) {
            requireWithinInlinePreviewLimit(version.blockKeys().size(), blockSize);
            downloadQuotaPort.checkWithinQuota(scope, version.versionKey());
            byte[] whole = assemble(retrieveBlocksPort.retrieveBlocks(version.blockKeys()));
            downloadQuotaPort.recordUsage(scope, version.versionKey(), whole.length);
            return new RangeContent(whole, 0, whole.length);
        }
        downloadQuotaPort.checkWithinQuota(scope, version.versionKey());
        long last = Math.min(end == null ? total - 1 : end, Math.min(total - 1, start + blockSize - 1));
        int firstBlock = (int) (start / blockSize);
        int lastBlock = (int) (last / blockSize);
        byte[] blocks = assemble(retrieveBlocksPort.retrieveBlocks(version.blockKeys().subList(firstBlock, lastBlock + 1)));
        int offset = (int) (start - (long) firstBlock * blockSize);
        byte[] slice = Arrays.copyOfRange(blocks, offset, offset + (int) (last - start + 1));
        downloadQuotaPort.recordUsage(scope, version.versionKey(), slice.length);
        return new RangeContent(slice, start, total);
    }
}
