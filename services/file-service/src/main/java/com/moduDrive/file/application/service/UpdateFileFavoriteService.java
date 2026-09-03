package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileFavoriteCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileFavoriteUseCase;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class UpdateFileFavoriteService implements UpdateFileFavoriteUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final FileFavoritePort fileFavoritePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public File updateFavorite(UpdateFileFavoriteCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));

        if (file.getOwnerId().equals(command.getCallerId())) {
            // The owner's favorite is a column on their own file row.
            file.markFavorite(command.isFavorite());
            return saveFilePort.saveFile(file);
        }

        // A shared file: favorite is per-user (see FileFavoritePort), so any grantee who can read
        // it may star it — for themselves only, never touching the owner's column.
        fileAccessGuard.requirePermission(file, command.getCallerId(), Permission.READ);
        if (command.isFavorite()) {
            fileFavoritePort.favorite(command.getCallerId(), file.getId());
        } else {
            fileFavoritePort.unfavorite(command.getCallerId(), file.getId());
        }
        // Reflect the caller's new state in the response; not persisted onto the file row.
        file.markFavorite(command.isFavorite());
        return file;
    }
}
