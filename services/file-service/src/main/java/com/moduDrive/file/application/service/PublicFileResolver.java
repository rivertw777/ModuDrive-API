package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Turns a link token into a file for the unauthenticated routes. Every rejection is the same
 * FILE_NOT_FOUND: an anonymous caller must not be able to tell "malformed token" from "wrong
 * token" from "right token, sharing switched off" from "right token, file trashed".
 * <p>
 * Shared by every public route so the metadata and the download paths can never disagree about
 * which tokens are live.
 */
@Component
@RequiredArgsConstructor
class PublicFileResolver {

    private final FindFilePort findFilePort;

    File resolve(String token) {
        return parseToken(token)
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
