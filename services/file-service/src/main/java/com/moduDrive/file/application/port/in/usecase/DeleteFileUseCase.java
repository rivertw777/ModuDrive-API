package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.DeleteFileCommand;

public interface DeleteFileUseCase {

    void deleteFile(DeleteFileCommand command);
}
