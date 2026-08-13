package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetLatestFileVersionsCommand;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface GetLatestFileVersionsUseCase {

    List<FileVersion> getLatestFileVersions(GetLatestFileVersionsCommand command);
}
