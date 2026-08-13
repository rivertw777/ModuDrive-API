package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

@Getter
public class GetPublicFileCommand {

    // Kept as the raw path-segment string, not UUID: an unauthenticated caller supplies this,
    // and a malformed value must fail the same way a well-formed-but-wrong one does (FILE_NOT_FOUND
    // from GetPublicFileService), not bubble up as a framework-level 500.
    private final String token;

    public GetPublicFileCommand(String token) {
        this.token = token;
    }
}
