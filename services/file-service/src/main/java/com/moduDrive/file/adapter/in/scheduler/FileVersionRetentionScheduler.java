package com.moduDrive.file.adapter.in.scheduler;

import com.moduDrive.file.application.port.in.usecase.PruneFileVersionsUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Clock-triggered inbound adapter. ShedLock, like TrashRetentionScheduler: the sweep walks every
 * file, so instances running it at once would only repeat each other's work (safely — each file is
 * locked and re-read). */
@Component
@RequiredArgsConstructor
class FileVersionRetentionScheduler {

    private final PruneFileVersionsUseCase pruneFileVersionsUseCase;

    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 2 * 60 * 1000) // hourly
    @SchedulerLock(name = "pruneFileVersions", lockAtMostFor = "PT50M", lockAtLeastFor = "PT1M")
    public void pruneFileVersions() {
        pruneFileVersionsUseCase.pruneFileVersions();
    }
}
