package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PrepareArchiveCommand;
import com.moduDrive.storage.application.port.in.usecase.PrepareArchiveUseCase;
import com.moduDrive.storage.application.port.out.ArchiveRequest;
import com.moduDrive.storage.application.port.out.ArchiveTokenPort;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort;
import com.moduDrive.storage.application.port.out.GetArchiveEntriesPort.ArchiveEntry;
import lombok.RequiredArgsConstructor;

import java.util.List;

@UseCase
@RequiredArgsConstructor
class PrepareArchiveService implements PrepareArchiveUseCase {

    private final GetArchiveEntriesPort getArchiveEntriesPort;
    private final DownloadQuotaPort downloadQuotaPort;
    private final ArchiveTokenPort archiveTokenPort;

    @Override
    public String prepare(PrepareArchiveCommand command) {
        // file-service has already enforced the zip size caps (ArchiveEntryCollector).
        ArchiveRequest request = new ArchiveRequest(command.getUserId(), command.getKey(), command.getFileIds());
        List<ArchiveEntry> files = getArchiveEntriesPort.getArchiveEntries(request).stream()
                .filter(entry -> !entry.isDirectory())
                .toList();
        // Same per-file counters a single download spends — a zip mustn't be a way around them.
        files.forEach(file -> downloadQuotaPort.checkWithinQuota(request.quotaScope(file.fileId()), file.s3Path()));
        return archiveTokenPort.issue(request);
    }
}
