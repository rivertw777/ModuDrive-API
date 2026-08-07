package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetFilesByCategoryCommand;
import com.moduDrive.file.domain.model.File;

import java.util.List;

public interface GetFilesByCategoryUseCase {

    List<File> getFilesByCategory(GetFilesByCategoryCommand command);
}
