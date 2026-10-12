package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileScopeCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileScopeUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
class UpdateFileScopeService implements UpdateFileScopeUseCase {

    private final FindFilePort findFilePort;
    private final SaveFilePort saveFilePort;
    private final FileAccessGuard fileAccessGuard;

    @Transactional
    @Override
    public File updateFileScope(UpdateFileScopeCommand command) {
        File file = findFilePort.findById(command.getFileId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));
        fileAccessGuard.requireOwner(file, command.getCallerId());

        if (command.getScope() == ShareScope.LINK) {
            // The domain makes links viewer-only anyway; this answers an editor-link request with
            // an error instead of silently downgrading it.
            if (command.getRole() != Role.VIEWER) {
                throw new BusinessException(FileExceptionCase.INVALID_LINK_ROLE);
            }
            file.enableLinkSharing();
        } else {
            boolean wasLinkShared = file.getAccessScope() == ShareScope.LINK;
            file.disableLinkSharing();
            // A descendant can hold its own LINK scope; restricting a folder means nothing under
            // it stays link-public. Email invites (FileShare rows) are a separate setting and are
            // left alone.
            if (file.isDirectory() && wasLinkShared) {
                restrictLinkedDescendants(file);
            }
            // Even when this file was already RESTRICTED: a LINK ancestor would keep it public.
            restrictLinkedAncestors(file);
        }

        return saveFilePort.saveFile(file);
    }

    /** A restricted file must be unreachable by link, so the LINK ancestors above it are
     * restricted too, with their subtrees (spec 2). Only the root-most one is swept: its subtree
     * already contains the others.
     * <p>
     * Ownership isn't re-checked per ancestor: a namespace has one owner, so every ancestor is
     * already the caller's. */
    private void restrictLinkedAncestors(File file) {
        for (File ancestor : fileAccessGuard.ancestorDirectories(file)) {
            if (ancestor.getAccessScope() != ShareScope.LINK) {
                continue;
            }
            ancestor.disableLinkSharing();
            saveFilePort.saveFile(ancestor);
            restrictLinkedDescendants(ancestor);
            return;
        }
    }

    private void restrictLinkedDescendants(File directory) {
        NamespaceId namespaceId = new NamespaceId(directory.getNamespaceId());
        for (File descendant : findFilePort.findByNamespaceIdAndPathStartingWith(namespaceId, directory.fullPath())) {
            if (descendant.getAccessScope() != ShareScope.LINK) {
                continue;
            }
            descendant.disableLinkSharing();
            saveFilePort.saveFile(descendant);
        }
    }
}
