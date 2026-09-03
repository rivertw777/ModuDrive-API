package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListDirectoryCommand;

public interface ListDirectoryUseCase {

    DirectoryPage listDirectory(ListDirectoryCommand command);
}
