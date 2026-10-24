package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ClaimPendingFileSharesUseCase;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@UseCase
@RequiredArgsConstructor
class ClaimPendingFileSharesService implements ClaimPendingFileSharesUseCase {

    private final FindFileSharePort findFileSharePort;
    private final SaveFileSharePort saveFileSharePort;
    private final DeleteFileSharePort deleteFileSharePort;
    private final FindMemberByEmailPort findMemberByEmailPort;

    @Transactional
    @Override
    public void claimPendingFileShares(ClaimPendingFileSharesCommand command) {
        // Don't trust the event's own claim that this member owns this email; ask member-service.
        if (!ownsEmail(command.getMemberId(), command.getGranteeEmail())) {
            log.warn("Rejected claim: memberId={} does not currently own the grantee email", command.getMemberId());
            return;
        }

        List<FileShare> pending = findFileSharePort.findPendingByGranteeEmail(command.getGranteeEmail());
        for (FileShare share : pending) {
            if (findFileSharePort.existsByFileIdAndSharedWithUserId(
                    new FileId(share.getFileId()), command.getMemberId())) {
                // The owner shared the file with this member directly meanwhile. Drop the invite
                // instead of failing the other claims: left unclaimed, its live token would survive
                // a later revoke of the real grant.
                log.warn("Deleting pending share {}: member {} already has a grant on this file",
                        share.getId(), command.getMemberId());
                deleteFileSharePort.deleteFileShare(new FileShareId(share.getId()));
                continue;
            }
            share.claim(command.getMemberId());
            saveFileSharePort.saveFileShare(share);
        }
    }

    private boolean ownsEmail(UUID memberId, String email) {
        return findMemberByEmailPort.findMemberIdByEmail(email)
                .filter(memberId::equals)
                .isPresent();
    }
}
