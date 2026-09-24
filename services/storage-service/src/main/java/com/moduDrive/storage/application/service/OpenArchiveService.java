package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.usecase.OpenArchiveUseCase;
import com.moduDrive.storage.application.port.out.ArchiveRequest;
import com.moduDrive.storage.application.port.out.ArchiveTokenPort;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort.ArchiveEntry;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streams the zip block by block, like a single download — only one decrypted block is ever in
 * memory. {@link ZipOutputStream} writes UTF-8 names and switches to ZIP64 on its own past 4GB or
 * 65,535 entries. The layout is re-resolved here rather than trusted from prepare, so access
 * revoked in between still counts. */
@UseCase
@RequiredArgsConstructor
class OpenArchiveService implements OpenArchiveUseCase {

    private static final DateTimeFormatter ZIP_NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ArchiveTokenPort archiveTokenPort;
    private final GetArchiveEntriesPort getArchiveEntriesPort;
    private final RetrieveBlocksPort retrieveBlocksPort;
    private final DownloadQuotaPort downloadQuotaPort;

    @Override
    public Archive open(String token) {
        ArchiveRequest request = archiveTokenPort.redeem(token)
                .orElseThrow(() -> new BusinessException(StorageExceptionCase.ARCHIVE_TOKEN_INVALID));
        List<ArchiveEntry> entries = getArchiveEntriesPort.getArchiveEntries(request);
        return new Archive(zipName(entries), out -> write(request, entries, out));
    }

    private void write(ArchiveRequest request, List<ArchiveEntry> entries, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        // Most of what people store (photos, video, office files) is already compressed.
        zip.setLevel(Deflater.BEST_SPEED);
        for (ArchiveEntry entry : entries) {
            zip.putNextEntry(new ZipEntry(entry.path()));
            if (!entry.isDirectory()) {
                // Not closed: closing it would close the zip underneath.
                CountingOutputStream counting = new CountingOutputStream(zip);
                try {
                    retrieveBlocksPort.streamBlocks(entry.s3Path(), entry.blockCount(), counting);
                } finally {
                    downloadQuotaPort.recordUsage(request.quotaScope(entry.fileId()), entry.s3Path(), counting.count());
                }
            }
            zip.closeEntry();
        }
        // finish, not close — the response stream belongs to the container.
        zip.finish();
    }

    /** One picked item → its own name ("사진.zip"); several → a timestamped name. */
    static String zipName(List<ArchiveEntry> entries) {
        List<String> tops = entries.stream()
                .map(entry -> entry.path().split("/", 2)[0])
                .distinct()
                .toList();
        if (tops.size() == 1) {
            return tops.getFirst() + ".zip";
        }
        return "ModuDrive-" + LocalDateTime.now().format(ZIP_NAME_TIME) + ".zip";
    }
}
