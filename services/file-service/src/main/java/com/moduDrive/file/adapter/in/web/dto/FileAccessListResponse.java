package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase.FileSharesView;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record FileAccessListResponse(
        UUID fileId,
        UUID ownerId,
        ShareScope scope,
        /** Direct rows first, then inherited rows root-most ancestor first. WEB's
         * {@code MemberAccessList} relies on this order: it takes the last inherited row of a
         * grantee as the nearest ancestor's, which is the effective role. */
        List<FileShareResponse> shares,
        /** Directories above this file that are currently "anyone with the link" — this file is
         * reachable through them. Empty unless an ancestor folder is link-shared. */
        List<InheritedLinkResponse> inheritedLinks,
        /** See {@link FileSharesView#hasSharedDescendant()}. */
        boolean hasSharedDescendant,
        /** So the client can word a confirmation ("이 파일에" vs "이 폴더에"). */
        boolean directory
) {
    /** A link-shared directory above this file, through which it is reachable whatever its own
     * scope. */
    public record InheritedLinkResponse(UUID fileId, String name, Role role) {}

    /** Shared by direct and inherited rows. A guest share has no member: its own email shows. */
    private static MemberSummary display(FileSharesView view, FileShare share) {
        UUID memberId = share.getSharedWithUserId();
        if (memberId == null) {
            return new MemberSummary(null, share.getGranteeEmail());
        }
        return view.memberSummaries().getOrDefault(memberId, FindMemberByIdPort.UNKNOWN_MEMBER);
    }

    public static FileAccessListResponse from(FileSharesView view) {
        List<FileShareResponse> shares = new ArrayList<>();

        for (var share : view.shares()) {
            var display = display(view, share);
            shares.add(FileShareResponse.from(share, display.email(), display.name()));
        }

        for (var inherited : view.inheritedShares()) {
            var display = display(view, inherited.share());
            shares.add(FileShareResponse.inherited(inherited.share(), display.email(), display.name(),
                    inherited.source().getId(), inherited.source().getName()));
        }

        List<InheritedLinkResponse> inheritedLinks = view.inheritedLinkSources().stream()
                .map(source -> new InheritedLinkResponse(source.getId(), source.getName(), source.getLinkRole()))
                .toList();

        return new FileAccessListResponse(
                view.file().getId(),
                view.file().getOwnerId(),
                view.file().getAccessScope(),
                shares,
                inheritedLinks,
                view.hasSharedDescendant(),
                view.file().isDirectory()
        );
    }
}
