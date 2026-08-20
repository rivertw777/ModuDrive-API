package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.ClaimPendingFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ClaimPendingFileSharesUseCase;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByEmailPort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
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
    private final FindMemberByEmailPort findMemberByEmailPort;

    @Transactional
    @Override
    public void claimPendingFileShares(ClaimPendingFileSharesCommand command) {
        // Kafka is internal-only, but a listener still shouldn't trust a payload's own claim of
        // "this memberId owns this email" — confirm it against member-service, the system of
        // record, before granting anything.
        if (!ownsEmail(command.getMemberId(), command.getGranteeEmail())) {
            log.warn("Rejected claim: memberId {} does not currently own email {}",
                    command.getMemberId(), command.getGranteeEmail());
            return;
        }

        List<FileShare> pending = findFileSharePort.findPendingByGranteeEmail(command.getGranteeEmail());
        for (FileShare share : pending) {
            if (findFileSharePort.existsByFileIdAndSharedWithUserId(
                    new FileId(share.getFileId()), command.getMemberId())) {
                // The owner already separately shared this same file with this member in the
                // meantime — one stale pending invite losing the race must not fail the rest of
                // this signup's claims.
                log.warn("Skipping pending share {}: member {} already has a grant on this file",
                        share.getId(), command.getMemberId());
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
