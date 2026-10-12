package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListFileSharesCommand;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ListFileSharesUseCase {

    FileSharesView listFileShares(ListFileSharesCommand command);

    /**
     * The file carries the owner and the scope; the owner is never a {@code shares} row.
     * {@code memberSummaries} is keyed by {@link FileShare#getSharedWithUserId()} and covers both
     * {@code shares} and {@code inheritedShares}.
     * <ul>
     *   <li>{@code inheritedShares}: grants on ancestor directories (member or guest), each with
     *       the directory it comes from. One row per (ancestor, grantee), never collapsed, even for
     *       a grantee with a direct row too: a full revoke needs to see every one.</li>
     *   <li>{@code inheritedLinkSources}: ancestor directories in LINK scope, through which
     *       "anyone with the link" reaches this file.</li>
     * </ul>
     */
    record FileSharesView(
            File file,
            List<FileShare> shares,
            List<InheritedShare> inheritedShares,
            List<File> inheritedLinkSources,
            Map<UUID, MemberSummary> memberSummaries,
            /** A directory with a shared entry somewhere under it: trashing the directory cuts
             * that share off too. Always false for a file. */
            boolean hasSharedDescendant) {

        public record InheritedShare(FileShare share, File source) {}
    }
}
