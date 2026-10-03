package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindFileVersionsPort {

    List<FileVersion> findByFileIdOrderByCreatedAtDesc(FileId fileId, int limit);

    /** Every version of the file, unordered and unbounded — a purge has to drop all their blocks. */
    List<FileVersion> findAllByFileId(FileId fileId);

    /** The version an upload already created — s3Path is unique per upload. */
    Optional<FileVersion> findByS3Path(String s3Path);

    /** One query for many versions — a zip of a large folder needs every file's current version at once. */
    List<FileVersion> findAllByIds(Collection<UUID> versionIds);
}
