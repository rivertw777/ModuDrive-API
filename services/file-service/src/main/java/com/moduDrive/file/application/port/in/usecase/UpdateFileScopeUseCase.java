package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.UpdateFileScopeCommand;
import com.moduDrive.file.domain.model.File;

public interface UpdateFileScopeUseCase {

    File updateFileScope(UpdateFileScopeCommand command);
}
