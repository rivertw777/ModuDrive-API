package com.moduDrive.notification.adapter.in.messaging;

import com.moduDrive.common.event.notification.FileSharedNotified;
import com.moduDrive.common.event.notification.NotificationDestinations;
import com.moduDrive.common.infrastructure.messaging.idempotency.ProcessedEvents;
import com.moduDrive.notification.application.port.in.command.RecordFileSharedNotificationCommand;
import com.moduDrive.notification.application.port.in.usecase.RecordFileSharedNotificationUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private RecordFileSharedNotificationUseCase recordFileSharedNotificationUseCase;
    @Mock
    private ProcessedEvents processedEvents;
    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Nested
    @DisplayName("파일 공유 알림 이벤트를 수신했을 때")
    class WhenFileSharedReceived {

        @Test
        void delegatesToRecordFileSharedNotificationUseCase() {
            given(processedEvents.isProcessed(NotificationDestinations.FILE_SHARED, "outbox-1")).willReturn(false);
            UUID eventId = UUID.randomUUID();
            UUID fileId = UUID.randomUUID();
            UUID recipientId = UUID.randomUUID();
            FileSharedNotified event = new FileSharedNotified(
                    eventId, fileId, recipientId, "report.pdf", "EDITOR", true, "홍길동", "owner@modudrive.com");

            notificationEventListener.onFileShared(event, "outbox-1");

            then(recordFileSharedNotificationUseCase).should().recordFileSharedNotification(
                    new RecordFileSharedNotificationCommand(
                            eventId, recipientId, fileId, "report.pdf", "EDITOR", true, "홍길동", "owner@modudrive.com"));
            then(processedEvents).should().markProcessed(NotificationDestinations.FILE_SHARED, "outbox-1");
        }
    }

    @Nested
    @DisplayName("이미 처리한 메시지가 다시 왔을 때")
    class WhenMessageWasAlreadyHandled {

        @Test
        void skipsItWithoutRecordingAnotherNotification() {
            given(processedEvents.isProcessed(NotificationDestinations.FILE_SHARED, "outbox-1")).willReturn(true);
            FileSharedNotified event = new FileSharedNotified(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "report.pdf", "EDITOR", true,
                    "홍길동", "owner@modudrive.com");

            notificationEventListener.onFileShared(event, "outbox-1");

            then(recordFileSharedNotificationUseCase).shouldHaveNoInteractions();
            then(processedEvents).should(never()).markProcessed(anyString(), anyString());
        }
    }
}
