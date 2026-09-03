package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListRecentFilesCommand;

import java.util.List;

public interface ListRecentFilesUseCase {

    List<FileView> listRecentFiles(ListRecentFilesCommand command);
}
