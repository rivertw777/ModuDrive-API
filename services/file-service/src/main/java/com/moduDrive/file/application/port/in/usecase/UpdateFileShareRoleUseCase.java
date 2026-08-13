package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.UpdateFileShareRoleCommand;
import com.moduDrive.file.domain.model.FileShare;

public interface UpdateFileShareRoleUseCase {

    FileShare updateFileShareRole(UpdateFileShareRoleCommand command);
}
