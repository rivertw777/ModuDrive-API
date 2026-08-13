package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetPublicFileCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicFileUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class GetPublicFileService implements GetPublicFileUseCase {

    private final FindFilePort findFilePort;

    /** Every rejection is the same FILE_NOT_FOUND: an unauthenticated caller must not be able to
     * tell "malformed token" from "wrong token" from "right token, sharing switched off" from
     * "right token, file trashed". */
    @Transactional(readOnly = true)
    @Override
    public File getPublicFile(GetPublicFileCommand command) {
        return parseToken(command.getToken())
                .flatMap(findFilePort::findByLinkToken)
                .filter(file -> file.getAccessScope() == ShareScope.LINK)
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
    }

    private Optional<UUID> parseToken(String token) {
        try {
            return Optional.of(UUID.fromString(token));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
