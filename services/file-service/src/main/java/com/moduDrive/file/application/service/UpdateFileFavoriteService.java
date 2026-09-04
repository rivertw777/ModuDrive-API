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

        // Anyone who can read the file may star it — for themselves only. A non-owner needs a
        // share; the owner always passes.
        if (!file.getOwnerId().equals(command.getCallerId())) {
            fileAccessGuard.requirePermission(file, command.getCallerId(), Permission.READ);
        }

        // Every star is a file_favorite row (the list orders by its created_at). The owner's row
        // is also mirrored onto file.favorite, the cheap flag every owner-facing listing already
        // reads without a per-file lookup.
        if (command.isFavorite()) {
            fileFavoritePort.favorite(command.getCallerId(), file.getId());
        } else {
            fileFavoritePort.unfavorite(command.getCallerId(), file.getId());
        }
        file.markFavorite(command.isFavorite());
        if (file.getOwnerId().equals(command.getCallerId())) {
            return saveFilePort.saveFile(file);
        }
        // Non-owner: file.favorite is set only to echo the caller's new state, not persisted.
        return file;
    }
}
