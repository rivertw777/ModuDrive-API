package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListFavoritesCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.in.usecase.ListFavoritesUseCase;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class ListFavoritesService implements ListFavoritesUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;
    private final FileFavoritePort fileFavoritePort;
    private final FileAccessGuard fileAccessGuard;

    // One findById per starred file (N+1).
    @Transactional(readOnly = true)
    @Override
    public List<FileView> listFavorites(ListFavoritesCommand command) {
        UUID userId = command.getUserId().value();
        findNamespacePort.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        // Most recently starred first. A star outlives a revoked share, so each file is checked
        // again: not removed, and still reachable for a file the caller doesn't own.
        record Starred(File file, LocalDateTime favoritedAt) {}

        return fileFavoritePort.favoritesByRecency(userId).stream()
                .flatMap(entry -> findFilePort.findById(new FileId(entry.fileId()))
                        .map(file -> new Starred(file, entry.favoritedAt()))
                        .stream())
                .filter(starred -> !starred.file().isRemoved())
                .map(starred -> {
                    File file = starred.file();
                    file.markFavorite(true);
                    Optional<FileView> view;
                    if (file.getOwnerId().equals(userId)) {
                        view = Optional.of(FileView.owned(file));
                    } else {
                        Role role = fileAccessGuard.effectiveRole(file, userId);
                        view = role == null ? Optional.empty() : Optional.of(FileView.shared(file, role));
                    }
                    return view.map(v -> v.withFavoritedAt(starred.favoritedAt()));
                })
                .flatMap(Optional::stream)
                .toList();
    }
}
