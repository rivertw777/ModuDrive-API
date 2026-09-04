package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

@Getter
public class GetPublicFileCommand {

    // Both kept as raw path/query strings, not UUID: an unauthenticated caller supplies them,
    // and a malformed value must fail the same way a well-formed-but-wrong one does
    // (FILE_NOT_FOUND from PublicFileResolver), not bubble up as a framework-level 500.
    private final String fileId;
    private final String key;

    public GetPublicFileCommand(String fileId, String key) {
        this.fileId = fileId;
        this.key = key;
    }
}
