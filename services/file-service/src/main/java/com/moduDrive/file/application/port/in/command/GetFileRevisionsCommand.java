package com.moduDrive.file.application.port.in.command;

import com.moduDrive.file.domain.model.File.FileId;
import lombok.Getter;

import java.util.UUID;

@Getter
public class GetFileRevisionsCommand {

    private final FileId fileId;
    private final int limit;

    public GetFileRevisionsCommand(UUID fileId, int limit) {
        this.fileId = new FileId(fileId);
        this.limit = limit;
    }
}
