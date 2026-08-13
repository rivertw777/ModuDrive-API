package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single place that answers "may this caller act on this file?". OWNER is never stored as a
 * {@link FileShare} row — it's {@code file.ownerId} — so both checks start from the file itself
 * and only fall back to the share table for non-owners.
 */
@Component
@RequiredArgsConstructor
class FileAccessGuard {

    private final FindFileSharePort findFileSharePort;

    void requireOwner(File file, UUID callerId) {
        if (!isOwner(file, callerId)) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    void requireRole(File file, UUID callerId, Role required) {
        if (isOwner(file, callerId)) return;

        Role granted = findFileSharePort
                .findByFileIdAndSharedWithUserId(new FileId(file.getId()), callerId)
                .map(FileShare::getRole)
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED));

        if (!granted.satisfies(required)) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    private boolean isOwner(File file, UUID callerId) {
        return callerId != null && callerId.equals(file.getOwnerId());
    }
}
