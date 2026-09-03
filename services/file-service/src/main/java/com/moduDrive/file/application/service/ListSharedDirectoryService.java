package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListSharedDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.in.usecase.ListSharedDirectoryUseCase;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class ListSharedDirectoryService implements ListSharedDirectoryUseCase {

    private final FindFilePort findFilePort;
    private final FileFavoritePort fileFavoritePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public List<FileView> listSharedDirectory(ListSharedDirectoryCommand command) {
        UUID callerId = command.getCallerId();
        File directory = findFilePort.findById(command.getDirectoryId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        // READ is enough to list contents; requirePermission already honours an inherited grant
        // from a directory further up, so a caller who was shared an ancestor can browse here too.
        fileAccessGuard.requirePermission(directory, callerId, Permission.READ);

        if (!directory.isDirectory()) {
            throw new BusinessException(FileExceptionCase.DIRECTORY_NOT_FOUND);
        }

        Set<UUID> favoriteIds = fileFavoritePort.favoriteFileIds(callerId);
        // Children of a shared folder inherit the caller's role on that folder; resolve it once
        // rather than walking every child's ancestor chain.
        Role inheritedRole = fileAccessGuard.effectiveRole(directory, callerId);

        return findFilePort
                .findByNamespaceIdAndPath(new NamespaceId(directory.getNamespaceId()), directory.fullPath())
                .stream()
                .filter(child -> child.getStatus() != FileStatus.DELETED)
                .map(child -> {
                    if (child.getOwnerId().equals(callerId)) {
                        return FileView.owned(child);
                    }
                    child.markFavorite(favoriteIds.contains(child.getId()));
                    return FileView.shared(child, inheritedRole);
                })
                .toList();
    }
}
