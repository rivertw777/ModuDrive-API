package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.GetPublicDescendantVersionsCommand;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface GetPublicDescendantVersionsUseCase {

    /** Versions of one file nested under a link-shared folder — storage-service resolves the
     * blocks to stream from this. */
    List<FileVersion> getPublicDescendantVersions(GetPublicDescendantVersionsCommand command);
}
