package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindFileSharePort {

    boolean existsByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId);

    boolean existsByFileIdAndGranteeEmail(FileId fileId, String granteeEmail);

    Optional<FileShare> findByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId);

    /** The user's grants on any of these files, in one query — a file and every folder above it
     * when resolving inherited access, or a folder's children when listing it. */
    List<FileShare> findBySharedWithUserIdAndFileIdIn(UUID sharedWithUserId, Collection<FileId> fileIds);

    /** {@link #findByFileIdAndSharedWithUserId} for a guest share, identified by its email. */
    Optional<FileShare> findByFileIdAndGranteeEmail(FileId fileId, String granteeEmail);

    /** A guest share by its invite token (see {@link FileShare#createPending}). No expiry: the
     * invite lasts until the share is revoked. */
    Optional<FileShare> findByToken(UUID token);

    Optional<FileShare> findByShareId(FileShareId shareId);

    List<FileShare> findByFileId(FileId fileId);

    /** Whether any of these files holds a share, in one query. */
    boolean existsByFileIdIn(List<FileId> fileIds);

    List<FileShare> findBySharedWithUserId(UUID sharedWithUserId);

    /** Pending guest shares (see {@link FileShare#createPending}) invited to this email, waiting
     * to be claimed by {@link FileShare#claim} once that email signs up. */
    List<FileShare> findPendingByGranteeEmail(String granteeEmail);
}
