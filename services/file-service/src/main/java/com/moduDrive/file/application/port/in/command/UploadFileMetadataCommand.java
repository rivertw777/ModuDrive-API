package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
import lombok.Getter;

@Getter
public class UploadFileMetadataCommand {

    private final Long userId;
    private final FileName name;
    private final FilePath path;
    private final FileOwnerId ownerId;
    private final FileIsDirectory isDirectory;

    public UploadFileMetadataCommand(Long userId, FileName name, FilePath path,
                                     FileOwnerId ownerId, FileIsDirectory isDirectory) {
        this.userId = userId;
        this.name = name;
        this.path = path;
        this.ownerId = ownerId;
        this.isDirectory = isDirectory;
    }
}
