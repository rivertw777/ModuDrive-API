package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion.FileVersionFileSize;
import com.moduDrive.file.domain.model.FileVersion.FileVersionHashes;
import com.moduDrive.file.domain.model.FileVersion.FileVersionUploadId;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class CommitFileUploadCommand {

    private final FileId fileId;
    private final UUID callerId;
    private final FileVersionUploadId uploadId;
    private final FileVersionFileSize fileSize;
    private final FileVersionHashes blocklist;

    public CommitFileUploadCommand(UUID fileId, UUID callerId, UUID uploadId, long fileSize, List<String> blocklist) {
        this.fileId = new FileId(fileId);
        this.callerId = callerId;
        this.uploadId = new FileVersionUploadId(uploadId);
        this.fileSize = new FileVersionFileSize(fileSize);
        this.blocklist = new FileVersionHashes(List.copyOf(blocklist));
    }
}
