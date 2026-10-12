package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.ListSharedWithMeCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.in.usecase.ListSharedWithMeUseCase;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File.FileId;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class ListSharedWithMeService implements ListSharedWithMeUseCase {

    private final FindFileSharePort findFileSharePort;
    private final FindFilePort findFilePort;
    private final FindMemberByIdPort findMemberByIdPort;
    private final FileFavoritePort fileFavoritePort;

    // Not @Transactional: no DB connection is held across the member-service lookups. One
    // findById and one member lookup per share (N+1).
    @Override
    public List<FileView> listSharedWithMe(ListSharedWithMeCommand command) {
        Set<UUID> favoriteIds = fileFavoritePort.favoriteFileIds(command.getSharedWithUserId());
        // Flat, like Drive: every item with a direct share, even one inside another shared folder.
        return findFileSharePort.findBySharedWithUserId(command.getSharedWithUserId())
                .stream()
                .flatMap(share -> findFilePort.findById(new FileId(share.getFileId()))
                        .filter(file -> !file.isRemoved())
                        .map(file -> {
                            file.markFavorite(favoriteIds.contains(file.getId()));
                            MemberSummary sharedBy = findMemberByIdPort.findMemberByIdOrUnknown(file.getOwnerId());
                            return new FileView(file, share.getRole(), sharedBy.name(), sharedBy.email(),
                                    share.getCreatedAt(), null, null);
                        })
                        .stream())
                .toList();
    }
}
