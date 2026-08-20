package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ClaimPendingFileSharesCommand {

    private final UUID memberId;
    private final String granteeEmail;

    public ClaimPendingFileSharesCommand(UUID memberId, String granteeEmail) {
        this.memberId = memberId;
        this.granteeEmail = granteeEmail;
    }
}
