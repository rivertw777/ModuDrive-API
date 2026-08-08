package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UpdateFileFavoriteCommand {

    private final FileId fileId;
    private final boolean favorite;

    public UpdateFileFavoriteCommand(UUID fileId, boolean favorite) {
        this.fileId = new FileId(fileId);
        this.favorite = favorite;
    }
}
