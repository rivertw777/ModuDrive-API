package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase.FileSharesView;
import com.moduDrive.file.domain.model.ShareScope;

import java.util.List;
import java.util.UUID;

public record FileAccessListResponse(
        UUID fileId,
        UUID ownerId,
        ShareScope scope,
        UUID linkToken,
        List<FileShareResponse> shares
) {
    public static FileAccessListResponse from(FileSharesView view) {
        return new FileAccessListResponse(
                view.file().getId(),
                view.file().getOwnerId(),
                view.file().getAccessScope(),
                view.file().getLinkToken(),
                view.shares().stream()
                        .map(share -> {
                            // A pending guest share has no member to look up — its own granteeEmail
                            // is the display email, and it has no member display name.
                            if (share.getSharedWithUserId() == null) {
                                return FileShareResponse.from(share, share.getGranteeEmail(), null);
                            }
                            var summary = view.memberSummaries().get(share.getSharedWithUserId());
                            return FileShareResponse.from(share, summary.email(), summary.name());
                        })
                        .toList()
        );
    }
}
