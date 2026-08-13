package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileFavoriteCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileFavoriteUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class UpdateFileFavoriteService implements UpdateFileFavoriteUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public File updateFavorite(UpdateFileFavoriteCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        // favorite is a single column on the owner's own file row, not per-viewer state, so a
        // shared VIEWER/EDITOR must not be able to flip it — restrict to OWNER (see code review).
        fileAccessGuard.requireOwner(file, command.getCallerId());

        file.markFavorite(command.isFavorite());
        return saveFilePort.saveFile(file);
    }
}
