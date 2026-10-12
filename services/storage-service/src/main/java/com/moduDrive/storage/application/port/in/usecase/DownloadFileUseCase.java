package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;

import java.io.OutputStream;

public interface DownloadFileUseCase {

    byte[] download(DownloadFileCommand command);

    /** Inline preview of {@code [start, end]} ({@code end} null = to the end of the file) — only
     * the blocks covering it are read, and the slice may come back shorter than asked for (see
     * {@link com.moduDrive.storage.application.service.BlockAssembler#readRange}). */
    RangeContent viewRange(DownloadFileCommand command, long start, Long end);

    /** Regular (non-inline) download path: writes blocks straight to {@code out} instead of
     * returning them, so the full file is never held in memory at once. */
    void downloadStream(DownloadFileCommand command, OutputStream out);
}
