package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindFileVersionsPort {

    List<FileVersion> findByFileIdOrderByCreatedAtDesc(FileId fileId, int limit);

    /** Every version of the file, unordered and unbounded — a purge has to drop all their blocks. */
    List<FileVersion> findAllByFileId(FileId fileId);

    /** The version a commit already created — the client picks a fresh uploadId per upload. */
    Optional<FileVersion> findByUploadId(UUID uploadId);

    /** One query for many versions — a zip of a large folder needs every file's current version at once. */
    List<FileVersion> findAllByIds(Collection<UUID> versionIds);

    /** Up to {@code limit} files, after {@code afterFileId} in id order, that may hold a version
     * {@link #findVersionsToPrune} drops — a candidate list for the retention sweep, not exact. */
    List<UUID> findFileIdsWithVersionsToPrune(LocalDateTime cutoff, int keep, UUID afterFileId, int limit);

    /** The file's versions past retention — none while the file isn't {@code UPLOADED}. Never its
     * current one; of the rest, any created before
     * {@code cutoff} or outside its newest {@code keep}. */
    List<FileVersion> findVersionsToPrune(FileId fileId, LocalDateTime cutoff, int keep);
}
