package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;
import com.moduDrive.file.application.port.in.usecase.RevokeFileShareUseCase;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class RevokeFileShareService implements RevokeFileShareUseCase {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final DeleteFileSharePort deleteFileSharePort;
    private final FindMemberByIdPort findMemberByIdPort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public void revokeFileShare(RevokeFileShareCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        Optional<FileShare> maybeShare = findFileSharePort.findByShareId(command.getShareId());
        if (maybeShare.isEmpty()) {
            // Already gone — most often this file's own ancestor cascade below finishing the job
            // before a second, redundant revoke for the same person arrives (a client that fires
            // the direct and ancestor revokes concurrently, rather than waiting for each to land),
            // or a retry of a call that in fact already succeeded. The caller already owns this
            // file (checked above) and their desired end state — this share doesn't exist — is
            // already true, so treat a re-request as success instead of an error nobody can act on.
            return;
        }

        // A share id from another file must not be revocable by this file's owner.
        FileShare fileShare = maybeShare
                .filter(share -> share.getFileId().equals(command.getFileId().value()))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_SHARE_NOT_FOUND));

        deleteFileSharePort.deleteFileShare(new FileShare.FileShareId(fileShare.getId()));
        revokeAncestorGrants(file, fileShare);
    }

    /** Deleting only the file's own grant would <em>promote</em> the grantee, not remove them:
     * {@code FileAccessGuard} treats a direct grant as authoritative and consults ancestors only
     * when there is none, so removing the direct row uncovers whatever the ancestor directory
     * still hands out — frequently a more generous role than the one just revoked. "Stop sharing
     * this with them" therefore has to reach the whole path above the file as well, which is what
     * the spec's 공유 권한 삭제 rule asks for. Every ancestor is swept, not just the nearest, since
     * any one of them left behind re-grants access on its own.
     * <p>
     * Doesn't re-check ownership on each ancestor row before deleting it — safe only because a
     * namespace has exactly one owner today (see {@code CommitFileUploadService}), so every
     * ancestor here is already the caller's own. A "shared folder someone else can upload into"
     * feature would break that assumption and need an explicit check here. */
    private void revokeAncestorGrants(File file, FileShare revoked) {
        UUID granteeId = revoked.getSharedWithUserId();
        List<FileId> missed = new ArrayList<>();
        for (File ancestor : fileAccessGuard.ancestorDirectories(file)) {
            FileId ancestorId = new FileId(ancestor.getId());
            Optional<FileShare> found = findGranteeShareByOwnIdentity(ancestorId, revoked);
            if (found.isPresent()) {
                deleteFileSharePort.deleteFileShare(new FileShare.FileShareId(found.get().getId()));
            } else if (granteeId != null) {
                // A pending-row revoke (granteeId == null) has no other identity to fall back to,
                // so it has nothing to add to this list — only a member-id miss can still turn
                // into an email-based hit below.
                missed.add(ancestorId);
            }
        }
        if (missed.isEmpty()) {
            return;
        }
        // One member-service round trip per revoke at most, not one per missed ancestor: the
        // email for this fallback can't come from `revoked` itself ({@link FileShare#claim}
        // always clears granteeEmail once a row is claimed, issue #323), and a revoke with
        // several ancestors that simply don't grant this person anything — the common case — must
        // not pay for a Feign call per level inside this open transaction.
        resolveEmail(granteeId).ifPresent(email -> missed.forEach(ancestorId ->
                findFileSharePort.findByFileIdAndGranteeEmail(ancestorId, email)
                        .ifPresent(grant -> deleteFileSharePort.deleteFileShare(new FileShare.FileShareId(grant.getId())))));
    }

    /** The same grantee's ancestor share, found without ever leaving this service: by member id
     * for a claimed grant, or by the revoked row's own email for a still-unclaimed guest invite.
     * Empty here doesn't mean "no ancestor grant" — a member-id miss still has the email fallback
     * in {@link #revokeAncestorGrants} to try, hoisted out of this method so it runs at most once
     * per revoke instead of once per ancestor. */
    private Optional<FileShare> findGranteeShareByOwnIdentity(FileId ancestorId, FileShare revoked) {
        if (revoked.getSharedWithUserId() != null) {
            return findFileSharePort.findByFileIdAndSharedWithUserId(ancestorId, revoked.getSharedWithUserId());
        }
        if (revoked.getGranteeEmail() != null) {
            return findFileSharePort.findByFileIdAndGranteeEmail(ancestorId, revoked.getGranteeEmail());
        }
        return Optional.empty();
    }

    /** Best-effort: a member-service hiccup must not block the revoke itself, only the
     * email-based half of the ancestor sweep above — {@code findMemberByIdOrUnknown} already
     * degrades to a null email on failure, logging it there instead of here. */
    private Optional<String> resolveEmail(UUID memberId) {
        return Optional.ofNullable(findMemberByIdPort.findMemberByIdOrUnknown(memberId).email());
    }
}
