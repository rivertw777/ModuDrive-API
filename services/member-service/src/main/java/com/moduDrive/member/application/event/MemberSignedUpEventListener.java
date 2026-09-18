package com.moduDrive.member.application.event;

import com.moduDrive.member.application.port.out.CreateNamespacePort;
import com.moduDrive.member.application.port.out.PublishMemberEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Two phases on purpose. The signed-up event is written to the outbox BEFORE_COMMIT, inside the
 * signup transaction, so it commits or rolls back with the member row and survives a Kafka outage
 * (#350). The Feign call to file-service stays AFTER_COMMIT, so a signup that didn't commit never
 * creates a namespace and the DB transaction doesn't hold its connection across an HTTP round
 * trip (#208). */
@Component
@RequiredArgsConstructor
class MemberSignedUpEventListener {

    private final CreateNamespacePort createNamespacePort;
    private final PublishMemberEventPort publishMemberEventPort;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void publishSignedUp(MemberSignedUpEvent event) {
        // Lets file-service auto-claim any pending guest share invited to this email before signup.
        publishMemberEventPort.publishSignedUp(event.memberId(), event.email());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void createNamespace(MemberSignedUpEvent event) {
        createNamespacePort.createNamespace(event.memberId());
    }
}
