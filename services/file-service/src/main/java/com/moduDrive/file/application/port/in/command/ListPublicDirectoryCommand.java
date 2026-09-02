package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

/** Token and parentId kept as raw Strings for the same reason as {@link GetPublicFileCommand}:
 * an unauthenticated caller supplies them, so a malformed value must 404 like a wrong one. */
@Getter
public class ListPublicDirectoryCommand {

    private final String token;
    /** The sub-directory to list, or null / blank / the folder's own id to list the shared
     * folder itself. */
    private final String parentId;

    public ListPublicDirectoryCommand(String token, String parentId) {
        this.token = token;
        this.parentId = parentId;
    }
}
