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
        return fileFavoritePort.favoriteFileIds(userId).stream()
                .map(id -> findFilePort.findById(new FileId(id)))
                .flatMap(Optional::stream)
                .filter(file -> file.getStatus() != FileStatus.DELETED)
                .map(file -> {
                    file.markFavorite(true);
                    if (file.getOwnerId().equals(userId)) {
                        return Optional.of(FileView.owned(file));
                    }
                    Role role = fileAccessGuard.effectiveRole(file, userId);
                    return role == null ? Optional.<FileView>empty() : Optional.of(FileView.shared(file, role));
                })
                .flatMap(Optional::stream)
                .toList();
    }
}
