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
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@UseCase
@RequiredArgsConstructor
class ListSharedWithMeService implements ListSharedWithMeUseCase {

    private static final MemberSummary UNKNOWN_MEMBER = new MemberSummary(null, null);

    private final FindFileSharePort findFileSharePort;
    private final FindFilePort findFilePort;
    private final FindMemberByIdPort findMemberByIdPort;
    private final FileFavoritePort fileFavoritePort;

    // Not @Transactional: findFilePort runs its own short read per share, so no DB connection
    // sits open across the sequential member-service lookups that enrich each row.
    // ponytail: one findById + one member lookup per share (N+1); fine at this scale.
    @Override
    public List<FileView> listSharedWithMe(ListSharedWithMeCommand command) {
        Set<UUID> favoriteIds = fileFavoritePort.favoriteFileIds(command.getSharedWithUserId());
        // Flat, matching Drive: every item this user has a *direct* share on shows up here, even
        // one nested under another of their own directly-shared folders — a stays listed here
        // even after its ancestor b is also shared to the same person (verified against real
        // Drive). ListSharedDirectoryService is the one that keeps b's own listing from
        // double-showing a — see the note there.
        return findFileSharePort.findBySharedWithUserId(command.getSharedWithUserId())
                .stream()
                .flatMap(share -> findFilePort.findById(new FileId(share.getFileId()))
                        .filter(file -> file.getStatus() != FileStatus.DELETED)
                        .map(file -> {
                            // The caller never owns a shared-with-me file, so their star is
                            // always the per-user one.
                            file.markFavorite(favoriteIds.contains(file.getId()));
                            MemberSummary sharedBy = lookupMember(file.getOwnerId());
                            return new FileView(file, share.getRole(), sharedBy.name(), sharedBy.email(),
                                    share.getCreatedAt(), null, null);
                        })
                        .stream())
                .toList();
    }

    /** Best-effort: a member-service hiccup degrades one row to "shared by unknown", never fails
     * the whole list the user needs to see what has been shared with them. */
    private MemberSummary lookupMember(UUID memberId) {
        try {
            return findMemberByIdPort.findMemberById(memberId);
        } catch (RuntimeException e) {
            log.warn("Failed to resolve sharer {} for shared-with-me list, showing as unknown", memberId, e);
            return UNKNOWN_MEMBER;
        }
    }
}
