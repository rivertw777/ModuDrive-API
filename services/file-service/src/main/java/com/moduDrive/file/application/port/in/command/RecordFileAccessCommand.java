package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.FileAccess.FileAccessFileId;
import com.moduDrive.file.domain.model.FileAccess.FileAccessUserId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class RecordFileAccessCommand {

    private final FileAccessUserId userId;
    private final FileAccessFileId fileId;

    public RecordFileAccessCommand(UUID userId, UUID fileId) {
        this.userId = new FileAccessUserId(userId);
        this.fileId = new FileAccessFileId(fileId);
    }
}
