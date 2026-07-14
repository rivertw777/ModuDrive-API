package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.SimpleUploadCommand;

public interface SimpleUploadUseCase {

    void simpleUpload(SimpleUploadCommand command);
}
