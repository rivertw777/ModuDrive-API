package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UploadFileMetadataCommand {

    private final UUID userId;
    private final FileName name;
    private final FilePath path;
    private final FileOwnerId ownerId;
    private final FileIsDirectory isDirectory;
    /** Caller's explicit consent to overwrite an active file already at this name/path (the
     * Drive-style "replace existing file" choice). False means "keep both" / a first-time
     * upload; a same-slot conflict with this false is rejected rather than silently replaced. */
    private final boolean replaceExisting;

    public UploadFileMetadataCommand(UUID userId, FileName name, FilePath path,
                                     FileOwnerId ownerId, FileIsDirectory isDirectory, boolean replaceExisting) {
        this.userId = userId;
        this.name = name;
        this.path = path;
        this.ownerId = ownerId;
        this.isDirectory = isDirectory;
        this.replaceExisting = replaceExisting;
    }
}
