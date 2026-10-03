package com.moduDrive.storage.application.port.in.command;

import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
public class PurgeStoredFileCommand {

    private final UUID fileId;
    private final List<StoredVersion> versions;

    public PurgeStoredFileCommand(UUID fileId, List<StoredVersion> versions) {
        this.fileId = fileId;
        this.versions = versions;
    }

    /** One version's blocks: {@code blockCount} of them under {@code s3Path}. */
    public record StoredVersion(String s3Path, int blockCount) {
    }
}
