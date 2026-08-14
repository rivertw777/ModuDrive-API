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

    /** The file carries the owner and the link/scope state; OWNER is never a {@code shares} row.
     * {@code memberSummaries} is keyed by {@link FileShare#getSharedWithUserId()} so the web
     * mapper can enrich each share's display info without a lookup per row (see #156). */
    record FileSharesView(File file, List<FileShare> shares, Map<UUID, MemberSummary> memberSummaries) {}
}
