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

/** Shared by the authenticated and the public download paths. */
final class BlockAssembler {

    private static final long MAX_INLINE_PREVIEW_BYTES = 100L * 1024 * 1024;

    private BlockAssembler() {
    }

    static byte[] assemble(List<byte[]> blocks) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        blocks.forEach(bos::writeBytes);
        return bos.toByteArray();
    }

    /** An inline preview without a Range assembles the whole file in memory, on a route the gateway
     * permits without auth. Checked before fetching; blockCount * blockSize overestimates, which is safe.
     * Only for inline previews: a regular download has no size limit. */
    static void requireWithinInlinePreviewLimit(int blockCount, int blockSizeBytes) {
        if ((long) blockCount * blockSizeBytes > MAX_INLINE_PREVIEW_BYTES) {
            throw new BusinessException(StorageExceptionCase.PREVIEW_TOO_LARGE);
        }
    }

    /** Reads only the blocks a {@code Range} covers: a {@code <video>} sends one per seek, and assembling
     * the whole file for each exhausts the heap. The slice is capped at one block's worth of bytes (at
     * most two blocks read), which RFC 9110 allows; media elements ask for the next range. Every block
     * but the last is exactly {@code blockSize} (file-service's commit check), so offsets map straight
     * to block indices. A start past the end (stale Range, or no recorded size) falls back to the
     * whole file, under the preview cap. */
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
