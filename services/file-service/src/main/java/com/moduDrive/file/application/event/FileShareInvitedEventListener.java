package com.moduDrive.file.application.event;

import com.moduDrive.file.application.port.out.PublishMailEventPort;
import com.moduDrive.file.application.port.out.PublishNotificationEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** AFTER_COMMIT so a rolled-back invite never produces a notification. */
@Component
@RequiredArgsConstructor
class FileShareInvitedEventListener {

    private final PublishMailEventPort publishMailEventPort;
    private final PublishNotificationEventPort publishNotificationEventPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onFileShareInvited(FileShareInvitedEvent event) {
        publishMailEventPort.publishShareInviteRequested(
                event.fileId(), event.granteeEmail(), event.fileName(), event.role().name(), event.linkToken());

        // A null granteeId means a guest-by-email invite: nobody owns that address on ModuDrive,
        // so there is no account to hang an in-app notification off. The mail is all they get.
        if (event.granteeId() != null) {
            publishNotificationEventPort.publishFileShared(
                    event.fileId(), event.granteeId(), event.fileName(), event.role().name());
        }
    }
}
