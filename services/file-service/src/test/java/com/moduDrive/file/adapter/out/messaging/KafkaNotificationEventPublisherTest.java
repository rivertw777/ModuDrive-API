package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.event.notification.FileSharedNotified;
import com.moduDrive.common.event.notification.NotificationTopics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private KafkaNotificationEventPublisher kafkaNotificationEventPublisher;

    @Captor
    private ArgumentCaptor<Object> payloadCaptor;

    @Nested
    @DisplayName("파일 공유 인앱 알림 이벤트를 발행할 때")
    class WhenPublishingFileShared {

        @Test
        void sendsPayloadToFileSharedTopicKeyedByRecipientId() {
            UUID fileId = UUID.randomUUID();
            UUID recipientId = UUID.randomUUID();
            given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                    .willReturn(CompletableFuture.completedFuture(null));

            kafkaNotificationEventPublisher.publishFileShared(fileId, recipientId, "report.pdf", "EDITOR");

            then(kafkaTemplate).should().send(
                    eq(NotificationTopics.FILE_SHARED), eq(recipientId.toString()), payloadCaptor.capture());
            assertThat(payloadCaptor.getValue())
                    .isInstanceOf(FileSharedNotified.class)
                    .satisfies(payload -> {
                        FileSharedNotified event = (FileSharedNotified) payload;
                        assertThat(event.eventId()).isNotNull();
                        assertThat(event.fileId()).isEqualTo(fileId);
                        assertThat(event.recipientId()).isEqualTo(recipientId);
                        assertThat(event.fileName()).isEqualTo("report.pdf");
                        assertThat(event.role()).isEqualTo("EDITOR");
                    });
        }

        @Test
        @DisplayName("발행할 때마다 새로운 eventId(멱등 키)를 생성한다")
        void generatesAFreshEventIdPerPublish() {
            UUID fileId = UUID.randomUUID();
            UUID recipientId = UUID.randomUUID();
            given(kafkaTemplate.send(any(String.class), any(String.class), any()))
                    .willReturn(CompletableFuture.completedFuture(null));

            kafkaNotificationEventPublisher.publishFileShared(fileId, recipientId, "report.pdf", "EDITOR");
            kafkaNotificationEventPublisher.publishFileShared(fileId, recipientId, "report.pdf", "EDITOR");

            then(kafkaTemplate).should(org.mockito.Mockito.times(2)).send(
                    eq(NotificationTopics.FILE_SHARED), eq(recipientId.toString()), payloadCaptor.capture());
            assertThat(payloadCaptor.getAllValues())
                    .extracting(payload -> ((FileSharedNotified) payload).eventId())
                    .doesNotHaveDuplicates();
        }
    }
}
