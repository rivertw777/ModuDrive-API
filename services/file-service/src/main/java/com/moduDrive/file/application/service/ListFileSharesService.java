package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase.FileSharesView.InheritedShare;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

        // A share on a directory above this file is inherited by it. Show those grants too so the
        // owner sees who really has access — but read-only, since the lever to change them is on
        // the directory, not here. Skip anyone already granted directly (the direct row wins).
        Set<UUID> directGrantees = shares.stream()
                .map(FileShare::getSharedWithUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        List<File> inheritedLinkSources = new ArrayList<>();
        // One row per inherited grantee. Ancestors arrive root-most first; keep the most generous
        // role (that's the effective access FileAccessGuard resolves) and, on a tie, the nearest
        // ancestor — matching Drive's "상속됨: <가장 가까운 폴더>".
        Map<UUID, InheritedShare> inheritedByGrantee = new LinkedHashMap<>();
        for (File ancestor : fileAccessGuard.ancestorDirectories(file)) {
            if (ancestor.getAccessScope() == ShareScope.LINK) {
                inheritedLinkSources.add(ancestor);
            }
            for (FileShare ancestorShare : findFileSharePort.findByFileId(new File.FileId(ancestor.getId()))) {
                UUID grantee = ancestorShare.getSharedWithUserId();
                if (grantee == null || directGrantees.contains(grantee)) {
                    continue;
                }
                inheritedByGrantee.merge(grantee, new InheritedShare(ancestorShare, ancestor), (existing, candidate) -> {
                    Role kept = existing.share().getRole();
                    Role incoming = candidate.share().getRole();
                    return kept == Role.EDITOR && incoming != Role.EDITOR ? existing : candidate;
                });
            }
        }
        List<InheritedShare> inheritedShares = new ArrayList<>(inheritedByGrantee.values());

        // Enrichment is member-service display data, not the file/share data itself — a lookup
        // failure (member deleted, member-service briefly down) must degrade that one row to
        // "unknown", never take down the whole share list an owner needs to see to revoke access.
        // A pending guest share has no sharedWithUserId to look up at all — it already carries its
        // own granteeEmail, so it's excluded here and read directly from the share row instead
        // (see FileAccessListResponse).
        var memberSummaries = Stream.concat(
                        shares.stream().map(FileShare::getSharedWithUserId),
                        inheritedShares.stream().map(i -> i.share().getSharedWithUserId()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), this::lookupMemberSummary));

        return new FileSharesView(file, shares, inheritedShares, inheritedLinkSources, memberSummaries);
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
