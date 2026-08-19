package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.IssueStreamTokenCommand;
import com.moduDrive.storage.application.port.in.usecase.IssueStreamTokenUseCase;
import com.moduDrive.storage.application.port.out.StreamTokenPort;
import lombok.RequiredArgsConstructor;

/** Mints the token without checking access first — the same permission check the header-based
 * flow already runs happens anyway when the token is redeemed via {@link DownloadFileService},
 * so duplicating it here would only be a second place for the two checks to drift apart. */
@UseCase
@RequiredArgsConstructor
class IssueStreamTokenService implements IssueStreamTokenUseCase {

    private final StreamTokenPort streamTokenPort;

    @Override
    public String issue(IssueStreamTokenCommand command) {
        return streamTokenPort.issue(command.getFileId(), command.getUserId());
    }
}
