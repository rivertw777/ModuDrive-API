package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetPublicFileCommand;
import com.moduDrive.file.domain.model.File;

public interface GetPublicFileUseCase {

    File getPublicFile(GetPublicFileCommand command);
}
