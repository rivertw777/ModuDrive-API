package com.moduDrive.member.application.event;

import com.moduDrive.member.application.port.out.CreateNamespacePort;
import com.moduDrive.member.application.port.out.PublishMemberEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** AFTER_COMMIT so neither the Feign call to file-service nor the Kafka publish ever fires for a
 * signup that didn't actually commit — and so the DB transaction (and its held connection) no
 * longer spans an HTTP round trip and a broker publish (#208). */
@Component
@RequiredArgsConstructor
class MemberSignedUpEventListener {

    private final CreateNamespacePort createNamespacePort;
    private final PublishMemberEventPort publishMemberEventPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMemberSignedUp(MemberSignedUpEvent event) {
        createNamespacePort.createNamespace(event.memberId());
        // Lets file-service auto-claim any pending guest share invited to this email before signup.
        publishMemberEventPort.publishSignedUp(event.memberId(), event.email());
    }
}
