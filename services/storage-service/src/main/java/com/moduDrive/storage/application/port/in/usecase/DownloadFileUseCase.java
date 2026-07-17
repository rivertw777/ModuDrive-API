package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;

public interface DownloadFileUseCase {

    byte[] download(DownloadFileCommand command);
}
