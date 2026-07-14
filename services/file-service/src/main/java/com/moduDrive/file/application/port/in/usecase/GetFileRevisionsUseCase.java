package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetFileRevisionsCommand;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface GetFileRevisionsUseCase {

    List<FileVersion> getFileRevisions(GetFileRevisionsCommand command);
}
