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
import com.moduDrive.file.domain.model.FileShare.FileShareSharedWithUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

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
    public FileShare shareFile(ShareFileCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getOwnerId().value());

        UUID granteeId = findMemberByEmailPort.findMemberIdByEmail(command.getEmail());

        // Owner is never a stored FileShare row (see answer.md §1) — inviting yourself would
        // create a duplicate identity for the same access level and break that invariant.
        if (granteeId.equals(command.getOwnerId().value())
                || findFileSharePort.existsByFileIdAndSharedWithUserId(command.getFileId(), granteeId)) {
            throw new BusinessException(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS);
        }

        FileShare fileShare = FileShare.create(
                new FileShareFileId(command.getFileId().value()),
                command.getOwnerId(),
                new FileShareSharedWithUserId(granteeId),
                command.getRole()
        );
        FileShare saved = saveFileSharePort.saveFileShare(fileShare);

        eventPublisher.publishEvent(new FileShareInvitedEvent(
                saved.getFileId(), saved.getOwnerId(), saved.getSharedWithUserId(), saved.getRole()));

        return saved;
    }
}
