package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

@Getter
public class GetPublicFileCommand {

    // Raw strings, not UUIDs: a malformed value from an anonymous caller must fail like a wrong
    // one (FILE_NOT_FOUND from PublicFileResolver), not as a 500.
    private final String fileId;
    private final String key;

    public GetPublicFileCommand(String fileId, String key) {
        this.fileId = fileId;
        this.key = key;
    }
}
