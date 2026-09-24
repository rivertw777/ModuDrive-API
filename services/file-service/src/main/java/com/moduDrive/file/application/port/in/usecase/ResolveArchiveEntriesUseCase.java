package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ResolveArchiveEntriesCommand;

import java.util.List;

public interface ResolveArchiveEntriesUseCase {

    List<ArchiveEntry> resolveArchiveEntries(ResolveArchiveEntriesCommand command);
}
