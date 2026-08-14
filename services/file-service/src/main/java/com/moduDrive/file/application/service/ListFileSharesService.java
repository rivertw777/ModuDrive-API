package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@UseCase
@RequiredArgsConstructor
class ListFileSharesService implements ListFileSharesUseCase {

    private static final MemberSummary UNKNOWN_MEMBER = new MemberSummary(null, null);

    private final FindFilePort findFilePort;
    private final FindFileSharePort findFileSharePort;
    private final FindMemberByIdPort findMemberByIdPort;
    private final FileAccessGuard fileAccessGuard;

    // Deliberately not @Transactional: findFilePort/findFileSharePort each run their own
    // short-lived read (Spring Data's default per-method transaction is enough for these simple
    // lookups), so no DB connection sits open across the N sequential member-service calls below.
    @Override
    public FileSharesView listFileShares(ListFileSharesCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        List<FileShare> shares = findFileSharePort.findByFileId(command.getFileId());

        // Enrichment is member-service display data, not the file/share data itself — a lookup
        // failure (member deleted, member-service briefly down) must degrade that one row to
        // "unknown", never take down the whole share list an owner needs to see to revoke access.
        var memberSummaries = shares.stream()
                .map(FileShare::getSharedWithUserId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), this::lookupMemberSummary));

        return new FileSharesView(file, shares, memberSummaries);
    }

    private MemberSummary lookupMemberSummary(UUID memberId) {
        try {
            return findMemberByIdPort.findMemberById(memberId);
        } catch (BusinessException e) {
            log.warn("Failed to resolve member {} for share display, showing as unknown", memberId, e);
            return UNKNOWN_MEMBER;
        }
    }
}
