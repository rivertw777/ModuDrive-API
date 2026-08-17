package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;

public interface PublicDownloadFileUseCase {

    byte[] downloadPublic(PublicDownloadFileCommand command);
}
