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

    // Not @Transactional: no DB connection is held across the member-service calls below.
    @Override
    public FileSharesView listFileShares(ListFileSharesCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        List<FileShare> shares = findFileSharePort.findByFileId(command.getFileId());

        // Every ancestor grant, one row per (ancestor, grantee), even for a grantee with a direct
        // row: the web needs them all to warn about and cascade a revoke, and hides redundant
        // rows itself.
        List<File> inheritedLinkSources = new ArrayList<>();
        List<InheritedShare> inheritedShares = new ArrayList<>();
        for (File ancestor : fileAccessGuard.ancestorDirectories(file)) {
            if (ancestor.getAccessScope() == ShareScope.LINK) {
                inheritedLinkSources.add(ancestor);
            }
            // Guest invites too: PublicFileResolver lets them reach the whole subtree.
            for (FileShare ancestorShare : findFileSharePort.findByFileId(new File.FileId(ancestor.getId()))) {
                inheritedShares.add(new InheritedShare(ancestorShare, ancestor));
            }
        }

        // A guest share has no member to look up; its granteeEmail is shown instead
        // (FileAccessListResponse).
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
        // An empty IN () is invalid SQL on some drivers.
        return !descendantIds.isEmpty() && findFileSharePort.existsByFileIdIn(descendantIds);
    }
}
