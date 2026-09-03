package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListSharedWithMeCommand;

import java.util.List;

public interface ListSharedWithMeUseCase {

    /** Files shared directly with the caller, each with its full "shared with me" context
     * (see {@link FileView}). */
    List<FileView> listSharedWithMe(ListSharedWithMeCommand command);
}
