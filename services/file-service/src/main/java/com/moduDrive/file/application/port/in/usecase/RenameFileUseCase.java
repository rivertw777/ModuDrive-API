package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.RenameFileCommand;
import com.moduDrive.file.domain.model.File;

public interface RenameFileUseCase {

    File renameFile(RenameFileCommand command);
}
