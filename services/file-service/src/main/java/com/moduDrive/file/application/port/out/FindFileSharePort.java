package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindFileSharePort {

    boolean existsByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId);

    boolean existsByFileIdAndGranteeEmail(FileId fileId, String granteeEmail);

    Optional<FileShare> findByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId);

    /** Resolves a pending guest share's own per-invite token (see {@link FileShare#createPending}) —
     * distinct from a file's {@code linkToken}, which {@code FindFilePort#findByLinkToken} resolves. */
    Optional<FileShare> findByToken(UUID token);

    Optional<FileShare> findByShareId(FileShareId shareId);

    List<FileShare> findByFileId(FileId fileId);

    List<FileShare> findBySharedWithUserId(UUID sharedWithUserId);
}
