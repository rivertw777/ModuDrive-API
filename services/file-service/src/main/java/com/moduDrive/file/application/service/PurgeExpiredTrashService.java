package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.usecase.PurgeExpiredTrashUseCase;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
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

    // ponytail: hardcoded retention, not a config value yet — same call as
    // FilePersistenceAdapter's GUEST_SHARE_TOKEN_TTL_DAYS; bump to @Value if a real need to tune
    // it per deployment shows up.
    private static final int TRASH_RETENTION_DAYS = 30;

    private final FindFilePort findFilePort;
    private final FilePurger filePurger;

    // Not @Transactional here on purpose: FilePurger.purgeRoot opens its own transaction per
    // root (see its javadoc). This sweep can span every namespace in the system — one bad root
    // must not roll back (or block committing) every other root already purged this run.
    @Override
    public void purgeExpiredTrash() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);
        List<File> expired = findFilePort.findByStatusAndUpdatedAtBefore(FileStatus.DELETED, cutoff);

        // Group by namespace, then within each namespace keep only the roots of a deleted
        // subtree (same trick as ListTrashService/EmptyTrashService) — purging a root directory
        // cascades onto every descendant regardless of its own age.
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
            filePurger.purgeRoot(root);
        } catch (RuntimeException e) {
            // One bad root (storage-service unreachable, a since-changed row) must not abort the
            // rest of the sweep — every other namespace's expired trash still needs purging.
            log.error("Failed to purge expired trash root fileId={} namespaceId={}", root.getId(), root.getNamespaceId(), e);
        }
    }
}
