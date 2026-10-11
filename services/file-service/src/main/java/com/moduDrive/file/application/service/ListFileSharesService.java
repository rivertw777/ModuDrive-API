package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListFileSharesCommand;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase;
import com.moduDrive.file.application.port.in.usecase.ListFileSharesUseCase.FileSharesView.InheritedShare;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UseCase
@RequiredArgsConstructor
class ListFileSharesService implements ListFileSharesUseCase {

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
        // owner sees who really has access. Kept even for someone already granted directly on
        // this file — removing just the direct row would otherwise look like it revoked their
        // access when the ancestor grant still lets them in (see RevokeInheritedDialog on the
        // web side, which needs this row to warn about and cascade that removal); the web layer
        // is the one that hides the redundant row when both exist, not this service.
        //
        // One row per (ancestor, grantee) pair — deliberately NOT collapsed to a single "nearest"
        // or "most generous" winner per grantee. Two independent ancestors (say a grandparent and
        // a parent directory) can each separately share the same person; a full revoke needs to
        // clear every one of those grants, not just whichever one would win a display tie-break —
        // otherwise the grant this service didn't mention keeps letting them in. (Attribution for
        // a *viewer's* "공유한 사용자" listing is a separate concern, resolved by
        // FileAccessGuard.access, which does pick a single nearest/most-generous grant —
        // that's unaffected by this.)
        List<File> inheritedLinkSources = new ArrayList<>();
        List<InheritedShare> inheritedShares = new ArrayList<>();
        for (File ancestor : fileAccessGuard.ancestorDirectories(file)) {
            if (ancestor.getAccessScope() == ShareScope.LINK) {
                inheritedLinkSources.add(ancestor);
            }
            // A pending guest invite (issue #313) belongs here just as much as a claimed member's
            // grant: PublicFileResolver already lets it reach this whole subtree (see
            // mintedForTargetOrAnAncestor), so hiding it from the owner would leave them unable to
            // even find, let alone revoke, an invite that's already granting access.
            for (FileShare ancestorShare : findFileSharePort.findByFileId(new File.FileId(ancestor.getId()))) {
                inheritedShares.add(new InheritedShare(ancestorShare, ancestor));
            }
        }

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
                .collect(Collectors.toMap(Function.identity(), findMemberByIdPort::findMemberByIdOrUnknown));

        boolean hasSharedDescendant = file.isDirectory() && hasSharedDescendant(file);

        return new FileSharesView(file, shares, inheritedShares, inheritedLinkSources, memberSummaries,
                hasSharedDescendant);
    }

    private boolean hasSharedDescendant(File directory) {
        List<File.FileId> descendantIds = findFilePort
                .findByNamespaceIdAndPathStartingWith(new NamespaceId(directory.getNamespaceId()), directory.fullPath())
                .stream()
                .map(descendant -> new File.FileId(descendant.getId()))
                .toList();
        // existsByFileIdIn on an empty id list is never called — an IN () clause is invalid SQL
        // on some drivers, and an empty subtree obviously has nothing shared in it anyway.
        return !descendantIds.isEmpty() && findFileSharePort.existsByFileIdIn(descendantIds);
    }
}
