package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListSharedDirectoryCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface ListSharedDirectoryUseCase {

    /** Children of a directory the caller can reach through a share (their own on the directory,
     * or an inherited one from a directory above it) — the entry point for browsing into a
     * folder that appears in "shared with me". */
    List<File> listSharedDirectory(ListSharedDirectoryCommand command);
}
