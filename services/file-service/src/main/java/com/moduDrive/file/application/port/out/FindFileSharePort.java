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

    /** The counterpart of {@link #findByFileIdAndSharedWithUserId} for a guest who hasn't
     * registered yet, whose only identity on the row is the invited email — see
     * {@link FileShare#createPending}. Needed wherever a grantee has to be followed across files
     * (revoking their ancestor grants too, see {@code RevokeFileShareService}), which the
     * existing {@code existsByFileIdAndGranteeEmail} can't answer: that one only reports
     * presence, not the row id to act on. */
    Optional<FileShare> findByFileIdAndGranteeEmail(FileId fileId, String granteeEmail);

    /** Resolves a pending guest share's own per-invite token (see {@link FileShare#createPending}) —
     * independent of link sharing (issue #303), which is judged purely by scope, not a token.
     * No expiry: the invite lasts until the share is revoked. */
    Optional<FileShare> findByToken(UUID token);

    Optional<FileShare> findByShareId(FileShareId shareId);

    List<FileShare> findByFileId(FileId fileId);

    /** Whether any of these files holds an active share — used to check a directory's whole
     * subtree at once (see {@code ListFileSharesService}) rather than one query per descendant. */
    boolean existsByFileIdIn(List<FileId> fileIds);

    List<FileShare> findBySharedWithUserId(UUID sharedWithUserId);

    /** Pending guest shares (see {@link FileShare#createPending}) invited to this email, waiting
     * to be claimed by {@link FileShare#claim} once that email signs up. */
    List<FileShare> findPendingByGranteeEmail(String granteeEmail);
}
