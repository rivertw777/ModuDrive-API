package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand;

public interface PurgeStoredFileUseCase {

    void purgeStoredFile(PurgeStoredFileCommand command);
}
