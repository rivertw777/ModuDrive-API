package com.moduDrive.file.application.port.in.command;

import lombok.Getter;

/** Raw Strings for the same reason as {@link GetPublicFileCommand}. {@code fileId} is the
 * directory to list; {@code key} is the optional guest-invite token, since a folder invite reaches
 * everything under the folder. */
@Getter
public class ListPublicDirectoryCommand {

    private final String fileId;
    private final String key;

    public ListPublicDirectoryCommand(String fileId, String key) {
        this.fileId = fileId;
        this.key = key;
    }
}
