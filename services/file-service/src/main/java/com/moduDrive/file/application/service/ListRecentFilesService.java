package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.ListRecentFilesCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.in.usecase.ListRecentFilesUseCase;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFileAccessPort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.domain.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class ListRecentFilesService implements ListRecentFilesUseCase {

    private final FindFileAccessPort findFileAccessPort;
    private final FindFilePort findFilePort;
    private final FindNamespacePort findNamespacePort;
    private final FileFavoritePort fileFavoritePort;
    private final FileAccessGuard fileAccessGuard;

    // One findById per access row (N+1).
    @Transactional(readOnly = true)
    @Override
    public List<FileView> listRecentFiles(ListRecentFilesCommand command) {
        UUID userId = command.getUserId().value();
        UUID ownNamespaceId = findNamespacePort.findByUserId(new NamespaceUserId(userId))
                .map(Namespace::getId)
                .orElse(null);

        Set<UUID> favoriteIds = fileFavoritePort.favoriteFileIds(userId);

        // role is the caller's on a file they don't own: resolved once, it both drops files they
        // can no longer reach and tells the client which actions to offer.
        record Accessed(File file, LocalDateTime accessedAt, Role role) {}

        return findFileAccessPort.findByUserIdOrderByAccessedAtDesc(userId, command.getLimit())
                .stream()
                .flatMap(access -> findFilePort.findById(new FileId(access.getFileId()))
                        .map(file -> new Accessed(file, access.getAccessedAt(), null))
                        .stream())
                .filter(accessed -> !accessed.file().isRemoved())
                // GetFileController doesn't record folders; this hides any recorded anyway.
                .filter(accessed -> !accessed.file().isDirectory())
                // Reachability is checked again: a share may have been revoked since.
                .map(accessed -> accessed.file().getNamespaceId().equals(ownNamespaceId) ? accessed
                        : new Accessed(accessed.file(), accessed.accessedAt(),
                                fileAccessGuard.effectiveRole(accessed.file(), userId)))
                .filter(accessed -> accessed.file().getNamespaceId().equals(ownNamespaceId) || accessed.role() != null)
                .map(accessed -> {
                    File file = accessed.file();
                    FileView view;
                    if (file.getOwnerId().equals(userId)) {
                        view = FileView.owned(file);
                    } else {
                        file.markFavorite(favoriteIds.contains(file.getId()));
                        view = FileView.shared(file, accessed.role());
                    }
                    return view.withAccessedAt(accessed.accessedAt());
                })
                .toList();
    }
}
