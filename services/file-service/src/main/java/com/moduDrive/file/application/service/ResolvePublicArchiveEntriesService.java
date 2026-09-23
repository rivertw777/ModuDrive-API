package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.command.ResolvePublicArchiveEntriesCommand;
import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;
import com.moduDrive.file.application.port.in.usecase.ResolvePublicArchiveEntriesUseCase;
import com.moduDrive.file.domain.model.File;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Anonymous counterpart of {@link ResolveArchiveEntriesService}: each picked item goes through
 * {@link PublicFileResolver}, same as a single public download. */
@UseCase
@RequiredArgsConstructor
class ResolvePublicArchiveEntriesService implements ResolvePublicArchiveEntriesUseCase {

    private final PublicFileResolver publicFileResolver;
    private final ArchiveEntryCollector archiveEntryCollector;

    @Transactional(readOnly = true)
    @Override
    public List<ArchiveEntry> resolvePublicArchiveEntries(ResolvePublicArchiveEntriesCommand command) {
        List<File> roots = command.getFileIds().stream().distinct()
                .map(id -> publicFileResolver.resolve(id, command.getKey()))
                .toList();
        return archiveEntryCollector.collect(roots);
    }
}
