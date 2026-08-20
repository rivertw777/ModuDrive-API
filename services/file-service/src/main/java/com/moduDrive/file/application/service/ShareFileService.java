package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.event.FileShareInvitedEvent;
import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.application.port.in.usecase.ShareFileUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareFileId;
import com.moduDrive.file.domain.model.FileShare.FileShareGranteeEmail;
import com.moduDrive.file.domain.model.FileShare.FileShareSharedWithUserId;
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

    /** No member owns the invited email, so there is no id to attach a normal {@link FileShare}
     * row to. Stays a {@code RESTRICTED} grant, scoped to just this one email, via
     * {@link FileShare#createPending}: the invite gets its own token independent of the file's
     * {@code linkToken}, so it never turns the file into "anyone with the link" and can be
     * revoked on its own without touching any other grant. */
    private void inviteGuest(File file, ShareFileCommand command) {
        if (findFileSharePort.existsByFileIdAndGranteeEmail(command.getFileId(), command.getEmail())) {
            throw new BusinessException(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS);
        }

        FileShare pending = FileShare.createPending(
                new FileShareFileId(command.getFileId().value()),
                command.getOwnerId(),
                new FileShareGranteeEmail(command.getEmail()),
                command.getRole()
        );
        FileShare saved = saveFileSharePort.saveFileShare(pending);

        eventPublisher.publishEvent(new FileShareInvitedEvent(
                saved.getFileId(), saved.getOwnerId(), null,
                command.getEmail(), file.getName(), saved.getRole(), saved.getToken()));
    }
}
