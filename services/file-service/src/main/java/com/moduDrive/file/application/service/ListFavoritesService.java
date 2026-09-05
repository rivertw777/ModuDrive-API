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
import com.moduDrive.file.domain.model.FileStatus;
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

    // ponytail: one findById per starred file (N+1); fine at this scale, batch-fetch by id if a
    // user's favorites grow large enough to matter.
    @Transactional(readOnly = true)
    @Override
    public List<FileView> listFavorites(ListFavoritesCommand command) {
        UUID userId = command.getUserId().value();
        // A namespace is still required — a user with no drive has no favorites either.
        findNamespacePort.findByUserId(command.getUserId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND));

        // Every star — the caller's own and the ones on files shared with them — is one
        // file_favorite row now, most recently starred first. Re-check each still resolves: not
        // trashed, and (for a file the caller doesn't own) still reachable — a star outlives a
        // revoked share, and "즐겨찾기" must reflect what they can open now. effectiveRole honours
        // an inherited folder grant, matching the READ check the favorite was written under.
        // A local record, not Map.entry — favoritedAt is DB-nullable for a star that predates the
        // column (see FileFavoriteJpaEntity), and Map.entry(K, V) throws on a null value.
        record Starred(File file, LocalDateTime favoritedAt) {}

        return fileFavoritePort.favoritesByRecency(userId).stream()
                .flatMap(entry -> findFilePort.findById(new FileId(entry.fileId()))
                        .map(file -> new Starred(file, entry.favoritedAt()))
                        .stream())
                .filter(starred -> starred.file().getStatus() != FileStatus.DELETED)
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
