package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetFileCommand;

public interface GetFileUseCase {

    FileView getFile(GetFileCommand command);
}
