package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.domain.model.FileShare;

public interface ShareFileUseCase {

    FileShare shareFile(ShareFileCommand command);
}
