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
                            var summary = view.memberSummaries().get(share.getSharedWithUserId());
                            return FileShareResponse.from(share, summary.email(), summary.name());
                        })
                        .toList()
        );
    }
}
