package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListSharedDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.in.usecase.ListSharedDirectoryUseCase;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@UseCase
@RequiredArgsConstructor
class ListSharedDirectoryService implements ListSharedDirectoryUseCase {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final FileFavoritePort fileFavoritePort;
    private final FindMemberByIdPort findMemberByIdPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public List<FileView> listSharedDirectory(ListSharedDirectoryCommand command) {
        UUID callerId = command.getCallerId();
        File directory = findFilePort.findById(command.getDirectoryId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        FileAccessGuard.Access access = fileAccessGuard.requirePermission(directory, callerId, Permission.READ);

        if (!directory.isDirectory()) {
            throw new BusinessException(FileExceptionCase.DIRECTORY_NOT_FOUND);
        }

        Set<UUID> favoriteIds = fileFavoritePort.favoriteFileIds(callerId);
        // Children inherit the directory's resolved role and its grant's "공유한 사용자/날짜"; the
        // directory is every child's nearest ancestor.
        Role inheritedRole = access.role();
        Optional<FileShare> grant = Optional.ofNullable(access.grant());
        MemberSummary sharedBy = findMemberByIdPort.findMemberByIdOrUnknown(directory.getOwnerId());
        LocalDateTime sharedAt = grant.map(FileShare::getCreatedAt).orElse(null);

        List<File> children = findFilePort
                .findByNamespaceIdAndPath(new NamespaceId(directory.getNamespaceId()), directory.fullPath())
                .stream()
                .filter(child -> !child.isRemoved())
                .toList();
        // A child's own direct grant to the caller, all children in one query.
        Map<UUID, FileShare> directGrants = findFileSharePort
                .findBySharedWithUserIdAndFileIdIn(callerId, children.stream().map(child -> new FileId(child.getId())).toList())
                .stream()
                .collect(Collectors.toMap(FileShare::getFileId, share -> share, (a, b) -> a));

        return children.stream()
                .map(child -> {
                    child.markFavorite(favoriteIds.contains(child.getId()));
                    if (child.getOwnerId().equals(callerId)) {
                        return FileView.owned(child);
                    }
                    // A direct grant wins outright, as in FileAccessGuard.access.
                    Optional<FileShare> direct = Optional.ofNullable(directGrants.get(child.getId()));
                    Role role = direct.map(FileShare::getRole).orElse(inheritedRole);
                    LocalDateTime sharedOn = direct.map(FileShare::getCreatedAt).orElse(sharedAt);
                    return new FileView(child, role, sharedBy.name(), sharedBy.email(), sharedOn, null, null);
                })
                .toList();
    }
}
