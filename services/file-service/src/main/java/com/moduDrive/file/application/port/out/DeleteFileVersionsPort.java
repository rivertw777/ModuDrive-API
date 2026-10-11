package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface DeleteFileVersionsPort {

    /** Deletes these version rows and their blocklists. Release their blocks first, in the same
     * transaction ({@link ReleaseBlocksPort}). */
    void deleteFileVersions(List<FileVersion> versions);
}
