package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListRecentFilesCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface ListRecentFilesUseCase {

    List<File> listRecentFiles(ListRecentFilesCommand command);
}
