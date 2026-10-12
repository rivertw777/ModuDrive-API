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
            // Already gone (an ancestor cascade or a retry got there first): the desired end
            // state already holds, so this succeeds.
            return;
        }

        // A share id from another file must not be revocable by this file's owner.
        FileShare fileShare = maybeShare
                .filter(share -> share.getFileId().equals(command.getFileId().value()))
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_SHARE_NOT_FOUND));

        deleteFileSharePort.deleteFileShare(new FileShare.FileShareId(fileShare.getId()));
        revokeAncestorGrants(file, fileShare);
    }

    /** Deleting only the direct grant would uncover the ancestors' grants, since
     * {@code FileAccessGuard} consults them only when there is none, so every ancestor's grant to
     * the same grantee goes too (spec: 공유 권한 삭제).
     * <p>
     * Ownership isn't re-checked per ancestor: a namespace has one owner, so every ancestor is
     * already the caller's. */
    private void revokeAncestorGrants(File file, FileShare revoked) {
        UUID granteeId = revoked.getSharedWithUserId();
        List<FileId> missed = new ArrayList<>();
        for (File ancestor : fileAccessGuard.ancestorDirectories(file)) {
            FileId ancestorId = new FileId(ancestor.getId());
            Optional<FileShare> found = findGranteeShareByOwnIdentity(ancestorId, revoked);
            if (found.isPresent()) {
                deleteFileSharePort.deleteFileShare(new FileShare.FileShareId(found.get().getId()));
            } else if (granteeId != null) {
                // Only a member-id miss can still match by email below.
                missed.add(ancestorId);
            }
        }
        if (missed.isEmpty()) {
            return;
        }
        // An ancestor may still hold this member's unclaimed guest invite by email. The email
        // isn't on the claimed row (claim clears it), so look it up once, not per ancestor.
        resolveEmail(granteeId).ifPresent(email -> missed.forEach(ancestorId ->
                findFileSharePort.findByFileIdAndGranteeEmail(ancestorId, email)
                        .ifPresent(grant -> deleteFileSharePort.deleteFileShare(new FileShare.FileShareId(grant.getId())))));
    }

    /** The same grantee's share on an ancestor, by member id or, for a guest invite, by email. */
    private Optional<FileShare> findGranteeShareByOwnIdentity(FileId ancestorId, FileShare revoked) {
        if (revoked.getSharedWithUserId() != null) {
            return findFileSharePort.findByFileIdAndSharedWithUserId(ancestorId, revoked.getSharedWithUserId());
        }
        if (revoked.getGranteeEmail() != null) {
            return findFileSharePort.findByFileIdAndGranteeEmail(ancestorId, revoked.getGranteeEmail());
        }
        return Optional.empty();
    }

    /** Best-effort: a member-service failure only skips the email-based part of the sweep. */
    private Optional<String> resolveEmail(UUID memberId) {
        return Optional.ofNullable(findMemberByIdPort.findMemberByIdOrUnknown(memberId).email());
    }
}
