package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;

import java.io.OutputStream;

public interface DownloadFileUseCase {

    byte[] download(DownloadFileCommand command);

    /** Inline preview of {@code [start, end]} ({@code end} null = to the end). Only the covering blocks
     * are read, and the slice may be shorter than asked for. */
    RangeContent viewRange(DownloadFileCommand command, long start, Long end);

    /** Regular download: writes blocks straight to {@code out}, so the file is never held in memory. */
    void downloadStream(DownloadFileCommand command, OutputStream out);
}
