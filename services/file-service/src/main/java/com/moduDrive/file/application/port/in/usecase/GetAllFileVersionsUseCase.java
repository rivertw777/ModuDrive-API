package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetAllFileVersionsCommand;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface GetAllFileVersionsUseCase {

    List<FileVersion> getAllFileVersions(GetAllFileVersionsCommand command);
}
