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

    @Scheduled(cron = "0 0 3 * * *") // daily at 3am
    @SchedulerLock(name = "purgeExpiredTrash", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void purgeExpiredTrash() {
        purgeExpiredTrashUseCase.purgeExpiredTrash();
    }
}
