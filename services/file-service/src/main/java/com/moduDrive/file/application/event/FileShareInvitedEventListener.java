package com.moduDrive.file.application.event;

import com.moduDrive.file.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** AFTER_COMMIT so a rolled-back invite never produces a notification. */
@Component
@RequiredArgsConstructor
class FileShareInvitedEventListener {

    private final PublishMailEventPort publishMailEventPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onFileShareInvited(FileShareInvitedEvent event) {
        publishMailEventPort.publishShareInviteRequested(
                event.fileId(), event.granteeEmail(), event.fileName(), event.role().name());
    }
}
