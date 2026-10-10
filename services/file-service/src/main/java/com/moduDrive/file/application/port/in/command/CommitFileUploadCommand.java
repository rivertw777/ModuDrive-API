package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.FileVersion.FileVersionFileSize;
import com.moduDrive.file.domain.model.FileVersion.FileVersionHashes;
import com.moduDrive.file.domain.model.FileVersion.FileVersionUploadId;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** {@code path}/{@code name} are where the file lands in the caller's own drive — the batch check
 * answered them. The service validates both, since they become rows. */
@Getter
public class CommitFileUploadCommand {

    private final UUID callerId;
    private final String path;
    private final String name;
    private final FileVersionUploadId uploadId;
    private final FileVersionFileSize fileSize;
    private final FileVersionHashes blocklist;

    public CommitFileUploadCommand(UUID callerId, String path, String name, UUID uploadId, long fileSize,
                                   List<String> blocklist) {
        this.callerId = callerId;
        this.path = path;
        this.name = name;
        this.uploadId = new FileVersionUploadId(uploadId);
        this.fileSize = new FileVersionFileSize(fileSize);
        this.blocklist = new FileVersionHashes(List.copyOf(blocklist));
    }
}
