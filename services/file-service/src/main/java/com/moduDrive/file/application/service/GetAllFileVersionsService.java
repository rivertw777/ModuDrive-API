package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetAllFileVersionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetAllFileVersionsUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
@RequiredArgsConstructor
class GetAllFileVersionsService implements GetAllFileVersionsUseCase {

    // ponytail: hardcoded ceiling instead of a true "no limit" query — matches
    // S3StorageAdapter's MAX_BLOCK_COUNT-style cap; bump if a file ever legitimately
    // accumulates more versions than this.
    private static final int ALL_VERSIONS_LIMIT = 10_000;

    private final FindFilePort findFilePort;
    private final FindFileVersionsPort findFileVersionsPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public List<FileVersion> getAllFileVersions(GetAllFileVersionsCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        // Ownership, not a delegated permission — see the command's javadoc for why.
        fileAccessGuard.requireOwner(file, command.getCallerId());

        return findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(command.getFileId(), ALL_VERSIONS_LIMIT);
    }
}
