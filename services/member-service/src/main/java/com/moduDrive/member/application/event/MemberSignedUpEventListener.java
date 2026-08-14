package com.moduDrive.member.application.event;

import com.moduDrive.member.application.port.out.PublishMailEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** AFTER_COMMIT so a rolled-back signup never triggers a verification email. */
@Component
@RequiredArgsConstructor
class MemberSignedUpEventListener {

    private final PublishMailEventPort publishMailEventPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMemberSignedUp(MemberSignedUpEvent event) {
        publishMailEventPort.publishVerificationRequested(
                event.memberId(), event.email(), event.name(), event.verificationToken());
    }
}
