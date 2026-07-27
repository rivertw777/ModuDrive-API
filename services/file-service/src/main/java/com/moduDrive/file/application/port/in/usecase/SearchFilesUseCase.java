package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.SearchFilesCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface SearchFilesUseCase {

    List<File> searchFiles(SearchFilesCommand command);
}
