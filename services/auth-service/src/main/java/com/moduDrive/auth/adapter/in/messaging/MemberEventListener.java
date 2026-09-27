package com.moduDrive.auth.adapter.in.messaging;

import com.moduDrive.auth.application.port.in.command.RevokeMemberAccessCommand;
import com.moduDrive.auth.application.port.in.usecase.RevokeMemberAccessUseCase;
import com.moduDrive.common.core.annotation.EventListener;
import com.moduDrive.common.event.member.MemberPasswordChanged;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.common.infrastructure.sqs.SqsAttributes;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.transaction.annotation.Transactional;

@EventListener
@RequiredArgsConstructor
class MemberEventListener {

    private final RevokeMemberAccessUseCase revokeMemberAccessUseCase;
    private final ProcessedEvents processedEvents;

    /** Skips a redelivery: running it again later would also end the sessions the member started
     * with the new password. @Transactional so the "handled" record commits with the forgotten
     * devices; the sessions live in Redis and don't roll back, but deleting them again is harmless. */
    @Transactional
    @SqsListener(MemberQueues.PASSWORD_CHANGED)
    void onPasswordChanged(MemberPasswordChanged event,
                           @Header(SqsAttributes.DEDUPLICATION_ID) String deduplicationId) {
        if (!processedEvents.claim(MemberQueues.PASSWORD_CHANGED, deduplicationId)) {
            return;
        }
        revokeMemberAccessUseCase.revokeMemberAccess(new RevokeMemberAccessCommand(event.memberId().toString()));
        processedEvents.markProcessed(MemberQueues.PASSWORD_CHANGED, deduplicationId);
    }
}
