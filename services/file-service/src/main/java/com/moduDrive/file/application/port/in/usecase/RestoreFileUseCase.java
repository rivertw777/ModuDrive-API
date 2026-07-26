package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.RestoreFileCommand;
import com.moduDrive.file.domain.model.File;

public interface RestoreFileUseCase {

    File restoreFile(RestoreFileCommand command);
}
