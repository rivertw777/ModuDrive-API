package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FilePath;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A whole upload selection — files, folders, or both — registered in one request. The bytes
 * are still sent per file afterwards; only the metadata rows are created here. */
@Getter
public class UploadBatchCommand {

    private final UUID userId;
    /** Full path of the folder being uploaded into, "/" for the drive root. */
    private final FilePath targetPath;
    private final List<Item> items;
    /** The user's answer per conflicting top-level file name. Empty on the first attempt. */
    private final Map<String, ConflictResolution> resolutions;

    public UploadBatchCommand(UUID userId, FilePath targetPath, List<Item> items,
                              Map<String, ConflictResolution> resolutions) {
        this.userId = userId;
        this.targetPath = targetPath;
        this.items = List.copyOf(items);
        this.resolutions = resolutions == null ? Map.of() : Map.copyOf(resolutions);
    }

    /** {@code relativePath} is "/"-separated and relative to {@code targetPath}; {@code size} is
     * only read for files. */
    public record Item(String relativePath, boolean directory, Long size) {}

    public enum ConflictResolution {
        REPLACE, KEEP_BOTH, SKIP
    }
}
