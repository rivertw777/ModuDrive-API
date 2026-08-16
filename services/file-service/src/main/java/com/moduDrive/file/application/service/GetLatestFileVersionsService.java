package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetLatestFileVersionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetLatestFileVersionsUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
class GetLatestFileVersionsService implements GetLatestFileVersionsUseCase {

    private final FindFilePort findFilePort;
    private final FindFileVersionsPort findFileVersionsPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public List<FileVersion> getLatestFileVersions(GetLatestFileVersionsCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        // This use case has exactly one caller: GetLatestFileVersionsController, the internal
        // route storage-service hits to resolve what to stream for an actual download — so the
        // permission checked here is DOWNLOAD, not READ (that's GetFileRevisionsService, the
        // tenant-facing revision-history listing).
        fileAccessGuard.requirePermission(file, command.getCallerId(), Permission.DOWNLOAD);

        return findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(command.getFileId(), command.getLimit());
    }
}
