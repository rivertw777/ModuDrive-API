package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetFileRevisionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetFileRevisionsUseCase;
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
class GetFileRevisionsService implements GetFileRevisionsUseCase {

    private final FindFilePort findFilePort;
    private final FindFileVersionsPort findFileVersionsPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public List<FileVersion> getFileRevisions(GetFileRevisionsCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requirePermission(file, command.getCallerId(), Permission.READ);

        return findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(command.getFileId(), command.getLimit());
    }
}
