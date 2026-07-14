package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.CreateDirectoryCommand;
import com.moduDrive.file.domain.model.File;

public interface CreateDirectoryUseCase {

    File createDirectory(CreateDirectoryCommand command);
}
