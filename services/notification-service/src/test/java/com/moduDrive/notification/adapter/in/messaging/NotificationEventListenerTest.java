package com.moduDrive.notification.adapter.in.messaging;

import com.moduDrive.common.event.notification.FileSharedNotified;
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

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private RecordFileSharedNotificationUseCase recordFileSharedNotificationUseCase;
    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Nested
    @DisplayName("파일 공유 알림 이벤트를 수신했을 때")
    class WhenFileSharedReceived {

        @Test
        void delegatesToRecordFileSharedNotificationUseCase() {
            UUID eventId = UUID.randomUUID();
            UUID fileId = UUID.randomUUID();
            UUID recipientId = UUID.randomUUID();
            FileSharedNotified event = new FileSharedNotified(
                    eventId, fileId, recipientId, "report.pdf", "EDITOR", true, "홍길동", "owner@modudrive.com");

            notificationEventListener.onFileShared(event);

            then(recordFileSharedNotificationUseCase).should().recordFileSharedNotification(
                    new RecordFileSharedNotificationCommand(
                            eventId, recipientId, fileId, "report.pdf", "EDITOR", true, "홍길동", "owner@modudrive.com"));
        }
    }
}
