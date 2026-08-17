package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.domain.model.FileShare;

import java.util.Optional;

public interface ShareFileUseCase {

    /** Empty when the invited email belongs to no ModuDrive member: there is no member id to
     * attach a {@link FileShare} row to, so the invite is delivered as a guest link instead. */
    Optional<FileShare> shareFile(ShareFileCommand command);
}
