package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;

import java.io.OutputStream;

public interface PublicDownloadFileUseCase {

    byte[] downloadPublic(PublicDownloadFileCommand command);

    /** Same reasoning as {@link DownloadFileUseCase#downloadStream}. */
    void downloadPublicStream(PublicDownloadFileCommand command, OutputStream out);
}
