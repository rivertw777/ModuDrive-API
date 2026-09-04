package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListFavoritesCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.in.usecase.ListFavoritesUseCase;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UseCase
@RequiredArgsConstructor
class ListFavoritesService implements ListFavoritesUseCase {

    private final FindNamespacePort findNamespacePort;
    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final FileFavoritePort fileFavoritePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional(readOnly = true)
    @Override
    public List<FileView> listFavorites(ListFavoritesCommand command) {
        UUID userId = command.getUserId().value();
        Namespace namespace = findNamespacePort.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        List<File> owned = findFilePort.findByNamespaceIdAndFavorite(new NamespaceId(namespace.getId()));
        Set<UUID> ownedIds = owned.stream().map(File::getId).collect(Collectors.toSet());

        // Files the user starred but doesn't own — per-user favorites, most recently starred
        // first (favoriteFileIds keeps that order). Skip any whose share has since been revoked
        // (the favorite row is then orphaned); mark the star, and carry the caller's role so the
        // client knows which actions to offer.
        Stream<FileView> shared = fileFavoritePort.favoriteFileIds(userId).stream()
                .filter(id -> !ownedIds.contains(id))
                .map(id -> findFilePort.findById(new FileId(id)))
                .flatMap(Optional::stream)
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                .filter(file -> findFileSharePort.existsByFileIdAndSharedWithUserId(
                        new FileId(file.getId()), userId))
                .map(file -> {
                    file.markFavorite(true);
                    return FileView.shared(file, fileAccessGuard.effectiveRole(file, userId));
                });

        // Owned favorites first (their query order — the file row has no "starred at"), then the
        // shared ones most-recently-starred first.
        return Stream.concat(owned.stream().map(FileView::owned), shared).toList();
    }
}
