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
            // Viewer-only is enforced by the domain (File#enableLinkSharing takes no role at all),
            // so this check exists only to answer an explicit editor-link request with a clear
            // error instead of silently downgrading it.
            if (command.getRole() != Role.VIEWER) {
                throw new BusinessException(FileExceptionCase.INVALID_LINK_ROLE);
            }
            file.enableLinkSharing();
        } else {
            // Capture before mutating: an already-RESTRICTED directory re-sent RESTRICTED is a
            // no-op for the directory itself, but without this check the sweep below would still
            // fire for a request that changed nothing.
            boolean wasLinkShared = file.getAccessScope() == ShareScope.LINK;
            file.disableLinkSharing();
            // Inheritance is a live computation over ancestor scope (FileAccessGuard), not a
            // stored flag, so restricting this directory alone already cuts off every descendant
            // that was only ever reachable *through* it. But a descendant can also hold its own,
            // independent LINK scope (shared directly, not merely inherited) — that one keeps
            // working on its own regardless of what this directory does, unless swept here too.
            // Restricting a folder must mean "nothing under it is link-public anymore", not
            // "unless some file underneath opted in on its own" — sweep the whole subtree.
            //
            // Email invites (FileShare rows) are deliberately never touched here — link sharing
            // and named invites are independent settings (issue #303's ancestor: the coupling bug
            // that used to wipe pending/claimed guest invites whenever link sharing was turned
            // off). Revoking an invite is RevokeFileShareService's job, not this one's.
            if (file.isDirectory() && wasLinkShared) {
                restrictLinkedDescendants(file);
            }
            // Deliberately outside the wasLinkShared guard: that flag only says whether *this*
            // file's own stored scope changed, while an ancestor directory can keep the file wide
            // open through FileAccessGuard's link fallback regardless. An already-RESTRICTED file
            // under a LINK folder is exactly the case that made issue #311 answer 200 while the
            // file stayed public, so a no-op re-request must still clean the path above it.
            // Naturally idempotent — with no LINK ancestor the walk writes nothing.
            restrictLinkedAncestors(file);
        }

        return saveFilePort.saveFile(file);
    }

    /** Restricting a file has to actually make it unreachable by link, and a LINK ancestor keeps
     * handing out VIEWER to anyone who asks — so every link-shared directory above it comes down
     * with it, each one sweeping its own subtree for the independent link scopes
     * {@link #restrictLinkedDescendants} exists to catch. Turning off a folder the caller never
     * named is the loud option, but the quiet one is worse: reporting RESTRICTED for a file that
     * is still public (spec 2 asks for exactly this cascade).
     * <p>
     * Only the root-most LINK ancestor is swept, and the walk stops there: its subtree already
     * contains every LINK ancestor below it, and {@code ancestorDirectories} returns root-most
     * first, so the first hit is the outermost one. Sweeping each LINK ancestor in turn rewrites the
     * same rows once per level — for a chain near the drive root, a large slice of it to restrict a
     * single file — and lands on exactly the same end state.
     * <p>
     * Doesn't re-check ownership on each ancestor before saving it — safe only because a
     * namespace has exactly one owner today (see {@code CommitFileUploadService}), so every
     * ancestor here is already the caller's own. A "shared folder someone else can upload into"
     * feature would break that assumption and need an explicit check here. */
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
