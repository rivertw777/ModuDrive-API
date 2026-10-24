package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface SaveFileVersionPort {

    FileVersion saveFileVersion(FileVersion fileVersion);
    /** {@link #saveFileVersion} for many versions, written in batches, in the given order. */
    List<FileVersion> saveFileVersions(List<FileVersion> fileVersions);
}
