package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.UpdateFileStatusCommand;
import com.moduDrive.file.domain.model.File;

public interface UpdateFileStatusUseCase {

    File updateFileStatus(UpdateFileStatusCommand command);
}
