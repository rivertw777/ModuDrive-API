package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ListFileSharesCommand;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileShare;

import java.util.List;

public interface ListFileSharesUseCase {

    FileSharesView listFileShares(ListFileSharesCommand command);

    /** The file carries the owner and the link/scope state; OWNER is never a {@code shares} row. */
    record FileSharesView(File file, List<FileShare> shares) {}
}
