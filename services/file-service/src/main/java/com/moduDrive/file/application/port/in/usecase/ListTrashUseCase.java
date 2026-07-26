package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListTrashCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface ListTrashUseCase {

    List<File> listTrash(ListTrashCommand command);
}
