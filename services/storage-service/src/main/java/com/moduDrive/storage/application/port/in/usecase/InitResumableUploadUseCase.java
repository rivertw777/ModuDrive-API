package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.InitResumableUploadCommand;

import java.util.UUID;

public interface InitResumableUploadUseCase {

    UUID initResumableUpload(InitResumableUploadCommand command);
}
