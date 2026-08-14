package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.event.mail.MailTopics;
import com.moduDrive.common.event.mail.ShareInviteMailRequested;
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
class KafkaMailEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private KafkaMailEventPublisher kafkaMailEventPublisher;

    @Nested
    @DisplayName("파일 공유 초대 메일 이벤트를 발행할 때")
    class WhenPublishingShareInviteRequested {

        @Test
        void sendsPayloadToShareInviteTopicKeyedByFileId() {
            UUID fileId = UUID.randomUUID();
            given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                    .willReturn(CompletableFuture.completedFuture(null));

            kafkaMailEventPublisher.publishShareInviteRequested(fileId, "grantee@modudrive.com", "report.pdf", "VIEWER");

            then(kafkaTemplate).should().send(
                    MailTopics.SHARE_INVITE_REQUESTED, fileId.toString(),
                    new ShareInviteMailRequested(fileId, "grantee@modudrive.com", "report.pdf", "VIEWER"));
        }
    }
}
