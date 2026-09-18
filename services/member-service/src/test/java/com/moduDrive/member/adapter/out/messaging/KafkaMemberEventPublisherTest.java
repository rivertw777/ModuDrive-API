package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.common.event.member.MemberTopics;
import com.moduDrive.common.infrastructure.outbox.OutboxEventRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class KafkaMemberEventPublisherTest {

    @Mock
    private OutboxEventRecorder outboxEventRecorder;
    @InjectMocks
    private KafkaMemberEventPublisher kafkaMemberEventPublisher;

    @Nested
    @DisplayName("회원가입 이벤트를 발행할 때")
    class WhenPublishingSignedUp {

        @Test
        void sendsPayloadToSignedUpTopicKeyedByEmail() {
            UUID memberId = UUID.randomUUID();

            kafkaMemberEventPublisher.publishSignedUp(memberId, "river@modudrive.com");

            then(outboxEventRecorder).should().record(
                    MemberTopics.SIGNED_UP, "river@modudrive.com",
                    new MemberSignedUp(memberId, "river@modudrive.com"));
        }
    }
}
