package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.CompleteResumableUploadCommand;

public interface CompleteResumableUploadUseCase {

    void completeResumableUpload(CompleteResumableUploadCommand command);
}
