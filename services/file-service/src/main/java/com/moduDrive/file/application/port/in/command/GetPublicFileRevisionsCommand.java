package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

/** Same reason as {@link GetPublicFileCommand} for keeping the token a raw String: the caller is
 * unauthenticated, so a malformed token must 404 like a wrong one rather than 500. */
@Getter
public class GetPublicFileRevisionsCommand {

    private final String token;
    private final int limit;

    public GetPublicFileRevisionsCommand(String token, int limit) {
        this.token = token;
        this.limit = limit;
    }
}
