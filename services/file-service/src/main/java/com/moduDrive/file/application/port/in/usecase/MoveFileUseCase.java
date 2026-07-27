package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.MoveFileCommand;
import com.moduDrive.file.domain.model.File;

public interface MoveFileUseCase {

    File moveFile(MoveFileCommand command);
}
