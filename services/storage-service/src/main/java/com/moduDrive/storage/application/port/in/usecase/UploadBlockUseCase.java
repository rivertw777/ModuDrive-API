package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.UploadBlockCommand;

public interface UploadBlockUseCase {

    void uploadBlock(UploadBlockCommand command);
}
