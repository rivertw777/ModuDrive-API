package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;

public interface ClaimPendingFileSharesUseCase {

    void claimPendingFileShares(ClaimPendingFileSharesCommand command);
}
