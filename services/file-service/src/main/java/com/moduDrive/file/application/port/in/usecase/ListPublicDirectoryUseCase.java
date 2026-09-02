package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListPublicDirectoryCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface ListPublicDirectoryUseCase {

    /** One level of the folder tree an anonymous visitor reached through a directory link. */
    List<File> listPublicDirectory(ListPublicDirectoryCommand command);
}
