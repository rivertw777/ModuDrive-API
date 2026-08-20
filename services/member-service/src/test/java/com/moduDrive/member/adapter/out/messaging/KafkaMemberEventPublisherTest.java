package com.moduDrive.member.adapter.out.messaging;

import com.moduDrive.common.event.member.MemberSignedUp;
import com.moduDrive.common.event.member.MemberTopics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class KafkaMemberEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private KafkaMemberEventPublisher kafkaMemberEventPublisher;

    @Nested
    @DisplayName("회원가입 이벤트를 발행할 때")
    class WhenPublishingSignedUp {

        @Test
        void sendsPayloadToSignedUpTopicKeyedByEmail() {
            UUID memberId = UUID.randomUUID();
            given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                    .willReturn(CompletableFuture.completedFuture(null));

            kafkaMemberEventPublisher.publishSignedUp(memberId, "river@modudrive.com");

            then(kafkaTemplate).should().send(
                    MemberTopics.SIGNED_UP, "river@modudrive.com",
                    new MemberSignedUp(memberId, "river@modudrive.com"));
        }
    }
}
