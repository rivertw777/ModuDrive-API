package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RestoreFileCommand;
import com.moduDrive.file.application.port.in.usecase.RestoreFileUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class RestoreFileService implements RestoreFileUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final DirectoryCascader directoryCascader;
    private final FileAccessGuard fileAccessGuard;
    private final FavoriteEnricher favoriteEnricher;

    @Transactional
    @Override
    public File restoreFile(RestoreFileCommand command) {
        // Locked, so a purge running now finishes first and this sees the tombstone, not TRASHED.
        File file = findFilePort.lockById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        // A purged file is a tombstone (status DELETED) — its content is gone; to the caller it
        // no longer exists, so this deliberately doesn't tell that case apart from "no such file"
        // with FILE_NOT_DELETED, the way a merely-live (never-trashed) file does.
        if (file.getStatus() == FileStatus.DELETED) {
            throw new BusinessException(FileExceptionCase.FILE_NOT_FOUND);
        }
        if (file.getStatus() != FileStatus.TRASHED) {
            throw new BusinessException(FileExceptionCase.FILE_NOT_DELETED);
        }

        file.restore();
        File saved = saveFilePort.saveFile(file);

        if (saved.isDirectory()) {
            directoryCascader.restore(new NamespaceId(saved.getNamespaceId()), saved.fullPath());
        }

        return favoriteEnricher.withFavorite(command.getCallerId(), saved);
    }
}
