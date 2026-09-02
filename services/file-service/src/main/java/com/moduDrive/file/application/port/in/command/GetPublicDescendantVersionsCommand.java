package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

/** Token and entryId kept as raw Strings (see {@link GetPublicFileCommand}): the caller is the
 * unauthenticated storage-service relay, and a malformed value must 404 like a wrong one. */
@Getter
public class GetPublicDescendantVersionsCommand {

    private final String token;
    private final String entryId;
    private final int limit;

    public GetPublicDescendantVersionsCommand(String token, String entryId, int limit) {
        this.token = token;
        this.entryId = entryId;
        this.limit = limit;
    }
}
