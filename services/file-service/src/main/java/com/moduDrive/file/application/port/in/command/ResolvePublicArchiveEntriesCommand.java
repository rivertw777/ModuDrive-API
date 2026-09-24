package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

import java.util.List;

/** Raw Strings for the same reason as {@link GetPublicFileRevisionsCommand}: a malformed id from
 * the anonymous relay must 404 like a wrong one, not 500. */
@Getter
public class ResolvePublicArchiveEntriesCommand {

    private final List<String> fileIds;
    private final String key;

    public ResolvePublicArchiveEntriesCommand(List<String> fileIds, String key) {
        this.fileIds = fileIds;
        this.key = key;
    }
}
