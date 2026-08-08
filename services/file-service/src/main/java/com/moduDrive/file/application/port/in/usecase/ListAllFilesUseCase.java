package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListAllFilesCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface ListAllFilesUseCase {

    List<File> listAllFiles(ListAllFilesCommand command);
}
