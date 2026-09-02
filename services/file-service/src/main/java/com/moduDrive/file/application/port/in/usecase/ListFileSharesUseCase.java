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
     * The file carries the owner and the link/scope state; OWNER is never a {@code shares} row.
     * {@code memberSummaries} is keyed by {@link FileShare#getSharedWithUserId()} so the web
     * mapper can enrich each share's display info without a lookup per row (see #156) — it covers
     * both {@code shares} and {@code inheritedShares}.
     * <p>
     * A directory share is inherited by everything under it, so this file's effective access is
     * its own grants plus whatever a directory above it grants:
     * <ul>
     *   <li>{@code inheritedShares} — member grants on an ancestor directory, each tagged with
     *       the directory they come from. Excludes anyone already in {@code shares} directly.</li>
     *   <li>{@code inheritedLinkSources} — ancestor directories currently in LINK scope, i.e.
     *       "anyone with the link" reaches this file through them. Restricting this file means
     *       turning their link off (there is no per-item inheritance break).</li>
     * </ul>
     */
    record FileSharesView(
            File file,
            List<FileShare> shares,
            List<InheritedShare> inheritedShares,
            List<File> inheritedLinkSources,
            Map<UUID, MemberSummary> memberSummaries) {

        public record InheritedShare(FileShare share, File source) {}
    }
}
