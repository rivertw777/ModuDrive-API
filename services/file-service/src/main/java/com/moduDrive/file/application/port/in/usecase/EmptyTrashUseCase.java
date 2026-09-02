package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.EmptyTrashCommand;

public interface EmptyTrashUseCase {

    void emptyTrash(EmptyTrashCommand command);
}
