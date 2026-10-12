package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.usecase.PruneFileVersionsUseCase;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File.FileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Spec 001: a file keeps its current version, and of the others only those under 30 days old and
 * among its newest 100 (Google Drive's rule). A dropped version releases its blocks; the
 * unreferenced-block sweep deletes the ones no other version uses, a day later. */
@Slf4j
@UseCase
@RequiredArgsConstructor
class PruneFileVersionsService implements PruneFileVersionsUseCase {

    static final Duration RETENTION = Duration.ofDays(30);
    static final int KEEP = 100;
    static final int BATCH_SIZE = 500;

    private final FindFileVersionsPort findFileVersionsPort;
    private final FileVersionPruner fileVersionPruner;

    // Not @Transactional: FileVersionPruner opens one transaction per file, so one bad file only
    // loses its own progress.
    @Override
    public void pruneFileVersions() {
        LocalDateTime cutoff = LocalDateTime.now().minus(RETENTION);
        UUID after = new UUID(0, 0);
        int pruned = 0;
        List<UUID> fileIds;
        do {
            fileIds = findFileVersionsPort.findFileIdsWithVersionsToPrune(cutoff, KEEP, after, BATCH_SIZE);
            for (UUID fileId : fileIds) {
                pruned += pruneSkippingFailures(new FileId(fileId), cutoff);
            }
            if (!fileIds.isEmpty()) {
                after = fileIds.getLast();
            }
        } while (fileIds.size() == BATCH_SIZE);
        if (pruned > 0) {
            log.info("Pruned {} file version(s) past retention", pruned);
        }
    }

    private int pruneSkippingFailures(FileId fileId, LocalDateTime cutoff) {
        try {
            return fileVersionPruner.prune(fileId, cutoff, KEEP);
        } catch (PessimisticLockingFailureException e) {
            // Postgres broke a lock cycle (e.g. a purge of this file between our reads); expected
            // now and then, the next run retries.
            log.warn("Pruning versions of fileId={} lost a lock race; retried next run", fileId.value());
            return 0;
        } catch (RuntimeException e) {
            log.error("Failed to prune versions of fileId={}", fileId.value(), e);
            return 0;
        }
    }
}
