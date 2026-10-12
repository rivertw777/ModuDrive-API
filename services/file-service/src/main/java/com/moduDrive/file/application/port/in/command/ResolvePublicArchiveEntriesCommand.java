package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

import java.util.List;

/** Raw Strings for the same reason as {@link GetPublicFileCommand}. */
@Getter
public class ResolvePublicArchiveEntriesCommand {

    private final List<String> fileIds;
    private final String key;

    public ResolvePublicArchiveEntriesCommand(List<String> fileIds, String key) {
        this.fileIds = fileIds;
        this.key = key;
    }
}
