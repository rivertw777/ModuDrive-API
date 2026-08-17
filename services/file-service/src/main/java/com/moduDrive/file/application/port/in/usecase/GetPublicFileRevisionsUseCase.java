package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetPublicFileRevisionsCommand;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface GetPublicFileRevisionsUseCase {

    List<FileVersion> getPublicFileRevisions(GetPublicFileRevisionsCommand command);
}
