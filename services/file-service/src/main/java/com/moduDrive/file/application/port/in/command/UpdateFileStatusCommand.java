package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion.FileVersionBlockCount;
import com.moduDrive.file.domain.model.FileVersion.FileVersionFileSize;
import com.moduDrive.file.domain.model.FileVersion.FileVersionS3Path;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UpdateFileStatusCommand {

    private final FileId fileId;
    private final UUID callerId;
    private final FileVersionFileSize fileSize;
    private final FileVersionBlockCount blockCount;
    private final FileVersionS3Path s3Path;

    public UpdateFileStatusCommand(UUID fileId, UUID callerId, Long fileSize, int blockCount, String s3Path) {
        this.fileId = new FileId(fileId);
        this.callerId = callerId;
        this.fileSize = new FileVersionFileSize(fileSize);
        this.blockCount = new FileVersionBlockCount(blockCount);
        this.s3Path = new FileVersionS3Path(s3Path);
    }
}
