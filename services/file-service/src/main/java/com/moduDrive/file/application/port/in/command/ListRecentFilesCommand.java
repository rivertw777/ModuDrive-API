package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.FileAccess.FileAccessUserId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class ListRecentFilesCommand {

    private static final int MAX_LIMIT = 100;

    private final FileAccessUserId userId;
    private final int limit;

    public ListRecentFilesCommand(UUID userId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        this.userId = new FileAccessUserId(userId);
        this.limit = limit;
    }
}
