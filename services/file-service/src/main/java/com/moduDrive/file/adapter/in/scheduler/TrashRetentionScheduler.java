package com.moduDrive.file.adapter.in.scheduler;

import com.moduDrive.file.application.port.in.usecase.PurgeExpiredTrashUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Clock-triggered inbound adapter. {@code @Scheduled} fires on every instance; ShedLock (see
 * {@code SchedulingLockConfig}) lets only one run the sweep per tick. */
@Component
@RequiredArgsConstructor
class TrashRetentionScheduler {

    private final PurgeExpiredTrashUseCase purgeExpiredTrashUseCase;

    // Noon KST, the same moment as the 3am UTC it ran at before the services moved to Korean time — and
    // not 3am KST, when demo is stopped for the night (.infra/night_stop.tf).
    @Scheduled(cron = "0 0 12 * * *")
    @SchedulerLock(name = "purgeExpiredTrash", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void purgeExpiredTrash() {
        purgeExpiredTrashUseCase.purgeExpiredTrash();
    }
}
