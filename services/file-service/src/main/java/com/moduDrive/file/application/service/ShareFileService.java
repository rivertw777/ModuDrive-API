package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.event.FileShareInvitedEvent;
import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.application.port.in.usecase.ShareFileUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareFileId;
import com.moduDrive.file.domain.model.FileShare.FileShareSharedWithUserId;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class ShareFileService implements ShareFileUseCase {

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final SaveFileSharePort saveFileSharePort;
    private final SaveFilePort saveFilePort;
    private final FindMemberByEmailPort findMemberByEmailPort;
    private final FileAccessGuard fileAccessGuard;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public Optional<FileShare> shareFile(ShareFileCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getOwnerId().value());

        Optional<UUID> granteeId = findMemberByEmailPort.findMemberIdByEmail(command.getEmail());
        if (granteeId.isEmpty()) {
            inviteGuest(file, command);
            return Optional.empty();
        }

        // The owner has no FileShare row of their own (ownership is file.ownerId, not a grant), so
        // inviting yourself would just be a pointless, confusing entry in your own sharing dialog.
        if (granteeId.get().equals(command.getOwnerId().value())) {
            throw new BusinessException(FileExceptionCase.FILE_SHARE_SELF_NOT_ALLOWED);
        }
        if (findFileSharePort.existsByFileIdAndSharedWithUserId(command.getFileId(), granteeId.get())) {
            throw new BusinessException(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS);
        }

        FileShare fileShare = FileShare.create(
                new FileShareFileId(command.getFileId().value()),
                command.getOwnerId(),
                new FileShareSharedWithUserId(granteeId.get()),
                command.getRole()
        );
        FileShare saved = saveFileSharePort.saveFileShare(fileShare);

        eventPublisher.publishEvent(new FileShareInvitedEvent(
                saved.getFileId(), saved.getOwnerId(), saved.getSharedWithUserId(),
                command.getEmail(), file.getName(), saved.getRole(), null));

        return Optional.of(saved);
    }

    /** No member owns the invited email, so there is no id to attach a {@link FileShare} row to.
     * Falls back to the file's existing "anyone with the link" mechanism — the mail carries that
     * link, and every link holder shares the same access; there is no per-guest revoke, only
     * turning link sharing off entirely. If the file is already link-shared, its live role is kept
     * rather than reset to the invited role: every existing link holder is on that link too, and a
     * single guest invite silently promoting or demoting all of them would be a bigger access change
     * than "invite one more guest". */
    private void inviteGuest(File file, ShareFileCommand command) {
        if (file.getAccessScope() != ShareScope.LINK) {
            file.enableLinkSharing(UUID.randomUUID(), command.getRole().value());
        }
        File saved = saveFilePort.saveFile(file);

        eventPublisher.publishEvent(new FileShareInvitedEvent(
                saved.getId(), command.getOwnerId().value(), null,
                command.getEmail(), saved.getName(), saved.getLinkRole(), saved.getLinkToken()));
    }
}
