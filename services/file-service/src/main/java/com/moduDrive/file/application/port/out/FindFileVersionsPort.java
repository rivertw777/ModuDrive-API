package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FindFileVersionsPort {

    List<FileVersion> findByFileIdOrderByCreatedAtDesc(FileId fileId, int limit);

    /** One query for many versions — a zip of a large folder needs every file's current version at once. */
    List<FileVersion> findAllByIds(Collection<UUID> versionIds);
}
