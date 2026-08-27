package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;

import java.io.OutputStream;

public interface DownloadFileUseCase {

    byte[] download(DownloadFileCommand command);

    /** Regular (non-inline) download path: writes blocks straight to {@code out} instead of
     * returning them, so the full file is never held in memory at once. */
    void downloadStream(DownloadFileCommand command, OutputStream out);
}
