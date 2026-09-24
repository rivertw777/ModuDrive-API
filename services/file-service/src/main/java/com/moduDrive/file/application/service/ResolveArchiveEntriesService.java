package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ResolveArchiveEntriesCommand;
import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;
import com.moduDrive.file.application.port.in.usecase.ResolveArchiveEntriesUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Every picked item must be downloadable by the caller — one that isn't fails the whole zip
 * (403/404) instead of silently leaving a hole the user asked for. */
@UseCase
@RequiredArgsConstructor
class ResolveArchiveEntriesService implements ResolveArchiveEntriesUseCase {

    private final FindFilePort findFilePort;
    private final FileAccessGuard fileAccessGuard;
    private final ArchiveEntryCollector archiveEntryCollector;

    @Transactional(readOnly = true)
    @Override
    public List<ArchiveEntry> resolveArchiveEntries(ResolveArchiveEntriesCommand command) {
        List<File> roots = command.getFileIds().stream().distinct()
                .map(id -> {
                    File file = findFilePort.findById(new FileId(id))
                            .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
                    fileAccessGuard.requirePermission(file, command.getCallerId(), Permission.DOWNLOAD);
                    return file;
                })
                .toList();
        return archiveEntryCollector.collect(roots);
    }
}
