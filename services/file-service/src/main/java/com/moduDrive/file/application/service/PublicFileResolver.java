package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Turns a token into a file for the unauthenticated routes. Two independent token spaces share
 * this one path: a file's own {@code linkToken} ("anyone with the link", scope LINK) and a
 * pending guest share's per-invite {@code token} (one specific invited email, scope stays
 * RESTRICTED — see {@link com.moduDrive.file.domain.model.FileShare#createPending}). Every
 * rejection is the same FILE_NOT_FOUND regardless of which space almost matched: an anonymous
 * caller must not be able to tell "malformed token" from "wrong token" from "right token, sharing
 * switched off" from "right token, file trashed" from "right token, invite revoked".
 * <p>
 * Shared by every public route so the metadata and the download paths can never disagree about
 * which tokens are live.
 */
@Component
@RequiredArgsConstructor
class PublicFileResolver {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;

    File resolve(String token) {
        return parseToken(token)
                .flatMap(this::resolveByToken)
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
    }

    private Optional<File> resolveByToken(UUID token) {
        Optional<File> linkShared = findFilePort.findByLinkToken(token)
                .filter(file -> file.getAccessScope() == ShareScope.LINK);
        if (linkShared.isPresent()) {
            return linkShared;
        }
        // A pending guest share's token stays live independently of the file's own accessScope —
        // it is not affected by the file ever having been, or never being, LINK-shared.
        return findFileSharePort.findByToken(token)
                .flatMap(share -> findFilePort.findById(new FileId(share.getFileId())));
    }

    private Optional<UUID> parseToken(String token) {
        try {
            return Optional.of(UUID.fromString(token));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
