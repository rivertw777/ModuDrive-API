package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileStatusCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileStatusUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.application.port.out.SaveFileVersionPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.FileVersionFileId;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class UpdateFileStatusService implements UpdateFileStatusUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final SaveFileVersionPort saveFileVersionPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public File updateFileStatus(UpdateFileStatusCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireRole(file, command.getCallerId(), Role.EDITOR);
        // EDITOR only proves the caller may write *some* version of this file — s3Path is
        // otherwise a free-form string storage-service hands back, so without this check an
        // EDITOR could point their own file at another file's storage location (e.g. one they
        // saw via a since-revoked VIEWER share) and read its bytes back out indefinitely.
        if (!command.getS3Path().value().startsWith("files/" + file.getId() + "/")) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }

        FileVersion version = FileVersion.create(
                new FileVersionFileId(file.getId()),
                command.getFileSize(),
                command.getBlockCount(),
                command.getS3Path()
        );
        FileVersion savedVersion = saveFileVersionPort.saveFileVersion(version);

        file.markUploaded(savedVersion.getId(), savedVersion.getFileSize());
        return saveFilePort.saveFile(file);
    }
}
