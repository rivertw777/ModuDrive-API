package com.moduDrive.notification.application.service;

import com.moduDrive.notification.application.port.in.command.RecordFileSharedNotificationCommand;
import com.moduDrive.notification.application.port.out.FindNotificationPort;
import com.moduDrive.notification.application.port.out.SaveNotificationPort;
import com.moduDrive.notification.domain.model.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class RecordFileSharedNotificationServiceTest {

    @Mock
    private FindNotificationPort findNotificationPort;
    @Mock
    private SaveNotificationPort saveNotificationPort;
    @InjectMocks
    private RecordFileSharedNotificationService recordFileSharedNotificationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private final UUID eventId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();
    private final RecordFileSharedNotificationCommand command =
            new RecordFileSharedNotificationCommand(eventId, recipientId, fileId, "report.pdf", "EDITOR");

    @Nested
    @DisplayName("처음 수신한 이벤트일 때")
    class WhenEventIsNew {

        @Test
        void savesAnUnreadNotification() {
            given(findNotificationPort.existsByEventId(command.getEventId())).willReturn(false);

            recordFileSharedNotificationService.recordFileSharedNotification(command);

            then(saveNotificationPort).should().insertNotification(notificationCaptor.capture());
            Notification saved = notificationCaptor.getValue();
            assertThat(saved.getEventId()).isEqualTo(eventId);
            assertThat(saved.getRecipientId()).isEqualTo(recipientId);
            assertThat(saved.getFileId()).isEqualTo(fileId);
            assertThat(saved.getFileName()).isEqualTo("report.pdf");
            assertThat(saved.getRole()).isEqualTo("EDITOR");
            assertThat(saved.isRead()).isFalse();
        }
    }

    @Nested
    @DisplayName("이미 기록된 이벤트가 재전달됐을 때")
    class WhenEventWasAlreadyRecorded {

        @Test
        void doesNotSaveADuplicate() {
            given(findNotificationPort.existsByEventId(command.getEventId())).willReturn(true);

            recordFileSharedNotificationService.recordFileSharedNotification(command);

            then(saveNotificationPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("동시 소비로 event_id 유니크 제약이 깨졌을 때")
    class WhenUniqueConstraintIsViolatedConcurrently {

        @Test
        void swallowsTheViolationAsAlreadyRecorded() {
            given(findNotificationPort.existsByEventId(command.getEventId())).willReturn(false);
            willThrow(new DuplicateKeyException("uk_notification_event_id"))
                    .given(saveNotificationPort).insertNotification(any(Notification.class));

            Throwable thrown = catchThrowable(
                    () -> recordFileSharedNotificationService.recordFileSharedNotification(command));

            assertThat(thrown).isNull();
        }
    }

    @Nested
    @DisplayName("이벤트가 손상되어 다른 제약 조건이 깨졌을 때")
    class WhenAnUnrelatedConstraintIsViolated {

        @Test
        void letsTheViolationPropagateInsteadOfSwallowingIt() {
            given(findNotificationPort.existsByEventId(command.getEventId())).willReturn(false);
            willThrow(new DataIntegrityViolationException("not-null constraint"))
                    .given(saveNotificationPort).insertNotification(any(Notification.class));

            Throwable thrown = catchThrowable(
                    () -> recordFileSharedNotificationService.recordFileSharedNotification(command));

            assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
