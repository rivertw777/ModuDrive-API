package com.moduDrive.file.application.port.in.usecase;

import com.moduDrive.file.application.port.in.command.ResolvePublicArchiveEntriesCommand;

import java.util.List;

public interface ResolvePublicArchiveEntriesUseCase {

    List<ArchiveEntry> resolvePublicArchiveEntries(ResolvePublicArchiveEntriesCommand command);
}
