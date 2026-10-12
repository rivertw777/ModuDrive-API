package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.domain.model.FileShare;

import java.util.Optional;

public interface ShareFileUseCase {

    /** Empty when the email belongs to no member: the invite then goes out as a guest link. */
    Optional<FileShare> shareFile(ShareFileCommand command);
}
