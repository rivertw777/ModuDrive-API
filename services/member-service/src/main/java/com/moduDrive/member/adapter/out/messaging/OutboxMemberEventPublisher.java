package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.core.annotation.EventPublisher;
import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.common.infrastructure.outbox.OutboxEventRecorder;
import com.moduDrive.member.application.port.out.PublishMemberEventPort;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@EventPublisher
@RequiredArgsConstructor
class OutboxMemberEventPublisher implements PublishMemberEventPort {

    private final OutboxEventRecorder outboxEventRecorder;

    @Override
    public void publishSignedUp(UUID memberId, String email) {
        outboxEventRecorder.record(MemberQueues.SIGNED_UP, email, new MemberSignedUp(memberId, email));
    }
}
