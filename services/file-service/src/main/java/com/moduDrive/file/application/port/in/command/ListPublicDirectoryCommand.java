package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

/** {@code fileId} and {@code key} kept as raw Strings for the same reason as
 * {@link GetPublicFileCommand}: an unauthenticated caller supplies them, so a malformed value
 * must 404 like a wrong one. {@code fileId} is the directory to list — the link's own folder or
 * any folder nested under it. */
@Getter
public class ListPublicDirectoryCommand {

    private final String fileId;
    private final String key;

    public ListPublicDirectoryCommand(String fileId, String key) {
        this.fileId = fileId;
        this.key = key;
    }
}
