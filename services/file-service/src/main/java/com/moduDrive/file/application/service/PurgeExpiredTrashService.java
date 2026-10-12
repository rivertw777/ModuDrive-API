package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.usecase.PurgeExpiredTrashUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@UseCase
@RequiredArgsConstructor
class PurgeExpiredTrashService implements PurgeExpiredTrashUseCase {

    private static final int TRASH_RETENTION_DAYS = 30;

    private final FindFilePort findFilePort;
    private final FilePurger filePurger;

    // Not @Transactional: FilePurger.purgeRoot runs each root in its own transaction, so one bad
    // root doesn't roll back the rest of the sweep.
    @Override
    public void purgeExpiredTrash() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);
        List<File> expired = findFilePort.findExpiredTrash(cutoff);

        // Per namespace, only the roots: purging a directory cascades onto its descendants
        // whatever their own age.
        expired.stream()
                .collect(Collectors.groupingBy(File::getNamespaceId))
                .forEach(this::purgeRootsInNamespace);
    }

    private void purgeRootsInNamespace(UUID namespaceId, List<File> expiredInNamespace) {
        Set<String> expiredDirectoryFullPaths = expiredInNamespace.stream()
                .filter(File::isDirectory)
                .map(File::fullPath)
                .collect(Collectors.toSet());

        expiredInNamespace.stream()
                .filter(file -> !expiredDirectoryFullPaths.contains(file.getPath()))
                .forEach(this::purgeRootSkippingFailures);
    }

    private void purgeRootSkippingFailures(File root) {
        try {
            // A system sweep: deletedBy stays null.
            filePurger.purgeRoot(root, null);
        } catch (RuntimeException e) {
            log.error("Failed to purge expired trash root fileId={} namespaceId={}", root.getId(), root.getNamespaceId(), e);
        }
    }
}
