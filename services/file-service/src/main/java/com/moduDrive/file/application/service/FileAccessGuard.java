package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindRolePermissionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single place that answers "may this caller act on this file?". Ownership is
 * {@code file.ownerId}, checked directly — there is no OWNER role or share row, and the owner
 * implicitly holds every permission.
 */
@Component
@RequiredArgsConstructor
class FileAccessGuard {

    private final FindFileSharePort findFileSharePort;
    private final FindRolePermissionsPort findRolePermissionsPort;

    void requireOwner(File file, UUID callerId) {
        if (!isOwner(file, callerId)) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    void requirePermission(File file, UUID callerId, Permission required) {
        if (isOwner(file, callerId)) {
            return;
        }
        Role granted = resolveRole(file, callerId);
        if (granted == null || !findRolePermissionsPort.findByRole(granted).contains(required)) {
            throw new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    /** Returns null when the caller has no explicit share on this file. A LINK file's
     * {@code linkRole} is deliberately not consulted here: these are the authenticated, fileId-only
     * routes, which never see the link token, so there is no way to tell "signed-in stranger who
     * has the link" from "signed-in stranger who doesn't" — granting on {@code callerId != null}
     * alone would hand every signed-in user permanent access to any file ever put in LINK mode,
     * surviving both share revocation and turning LINK back off. Anonymous/token-holding access to
     * a LINK file goes through the public routes instead, which do check the token. */
    private Role resolveRole(File file, UUID callerId) {
        if (callerId == null) {
            return null;
        }
        return findFileSharePort
                .findByFileIdAndSharedWithUserId(new FileId(file.getId()), callerId)
                .map(FileShare::getRole)
                .orElse(null);
    }

    private boolean isOwner(File file, UUID callerId) {
        return callerId != null && callerId.equals(file.getOwnerId());
    }
}
