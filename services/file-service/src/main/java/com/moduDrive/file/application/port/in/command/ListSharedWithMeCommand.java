package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

import java.util.UUID;

@Getter
public class ListSharedWithMeCommand {

    private final UUID sharedWithUserId;

    public ListSharedWithMeCommand(UUID sharedWithUserId) {
        this.sharedWithUserId = sharedWithUserId;
    }
}
