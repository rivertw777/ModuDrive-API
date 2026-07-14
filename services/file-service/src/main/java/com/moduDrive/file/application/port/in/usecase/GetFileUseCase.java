package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetFileCommand;
import com.moduDrive.file.domain.model.File;

public interface GetFileUseCase {

    File getFile(GetFileCommand command);
}
