package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListDirectoryCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface ListDirectoryUseCase {

    List<File> listDirectory(ListDirectoryCommand command);
}
