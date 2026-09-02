package com.moduDrive.file.adapter.in.scheduler;

import com.moduDrive.file.application.port.in.usecase.PurgeExpiredTrashUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Inbound adapter triggered by the clock instead of HTTP — the scheduler equivalent of a
 * controller, so it does nothing but map the trigger onto the use case.
 *
 * file-service runs as multiple instances behind Eureka/the gateway, and {@code @Scheduled}
 * fires independently on every one of them — without a lock, every instance would run the same
 * sweep at 3am and race to purge the same rows. {@code @SchedulerLock} (ShedLock) makes only one
 * instance's invocation actually run per tick; the rest see the lock held and skip. Vendor-
 * agnostic on purpose (see {@code SchedulingLockConfig}) — a Postgres-only advisory lock would
 * have been simpler but breaks the moment the database is swapped. */
@Component
@RequiredArgsConstructor
class TrashRetentionScheduler {

    private final PurgeExpiredTrashUseCase purgeExpiredTrashUseCase;

    @Scheduled(cron = "0 0 3 * * *") // daily at 3am
    @SchedulerLock(name = "purgeExpiredTrash", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void purgeExpiredTrash() {
        purgeExpiredTrashUseCase.purgeExpiredTrash();
    }
}
