package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

/** Same reason as {@link GetPublicFileCommand} for keeping {@code fileId}/{@code key} raw
 * Strings: the caller is the unauthenticated storage-service relay, so a malformed value must
 * 404 like a wrong one rather than 500. */
@Getter
public class GetPublicFileRevisionsCommand {

    private final String fileId;
    private final String key;
    private final int limit;

    public GetPublicFileRevisionsCommand(String fileId, String key, int limit) {
        this.fileId = fileId;
        this.key = key;
        this.limit = limit;
    }
}
