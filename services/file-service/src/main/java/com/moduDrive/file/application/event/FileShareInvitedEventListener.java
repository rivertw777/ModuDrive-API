package com.moduDrive.file.application.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AFTER_COMMIT so a rolled-back invite never produces a notification.
 * ponytail: logging stub — swap the body for a Feign call to notification-service (same shape as
 * auth→member) when delivery is actually wanted; an outbox/queue only once one consumer isn't enough.
 */
@Slf4j
@Component
class FileShareInvitedEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onFileShareInvited(FileShareInvitedEvent event) {
        log.info("File share invited: fileId={} granterId={} granteeId={} role={}",
                event.fileId(), event.granterId(), event.granteeId(), event.role());
    }
}
