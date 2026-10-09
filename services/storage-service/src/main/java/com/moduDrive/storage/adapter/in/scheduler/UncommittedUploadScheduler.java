package com.moduDrive.storage.adapter.in.scheduler;

import com.moduDrive.storage.application.port.in.usecase.PurgeUncommittedUploadsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Clock-triggered inbound adapter. No ShedLock: the sweep claims each block atomically in Redis,
 * so instances running it at the same time split the work instead of repeating it. */
@Component
@RequiredArgsConstructor
class UncommittedUploadScheduler {

    private final PurgeUncommittedUploadsUseCase purgeUncommittedUploadsUseCase;

    @Scheduled(fixedDelay = 60 * 60 * 1000) // hourly
    public void purgeUncommittedUploads() {
        purgeUncommittedUploadsUseCase.purgeUncommittedUploads();
    }
}
