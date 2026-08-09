package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.PurgeFileCommand;

public interface PurgeFileUseCase {

    void purgeFile(PurgeFileCommand command);
}
