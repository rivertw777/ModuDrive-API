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
        // Only the file's owner ever reaches this: UploadFileMetadataService creates the PENDING
        // row in the caller's own namespace, so the fileId being completed here always belongs to
        // whoever started the upload — there is no shared-EDITOR re-upload flow to authorize.
        fileAccessGuard.requireOwner(file, command.getCallerId());
        // s3Path is otherwise a free-form string storage-service hands back — this guard is
        // defense-in-depth against a buggy or compromised storage-service pointing a completed
        // upload at another file's storage location, not against the caller (already proven to be
        // the owner above).
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
