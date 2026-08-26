package com.moduDrive.notification.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.notification.application.port.in.command.MarkNotificationReadCommand;
import com.moduDrive.notification.application.port.out.FindNotificationPort;
import com.moduDrive.notification.application.port.out.SaveNotificationPort;
import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.exception.NotificationExceptionCase;
import com.moduDrive.notification.fixture.NotificationTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class MarkNotificationReadServiceTest {

    @Mock
    private FindNotificationPort findNotificationPort;
    @Mock
    private SaveNotificationPort saveNotificationPort;
    @InjectMocks
    private MarkNotificationReadService markNotificationReadService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private final UUID notificationId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final MarkNotificationReadCommand command = new MarkNotificationReadCommand(notificationId, recipientId);

    @Nested
    @DisplayName("수신자 본인이 자신의 알림을 읽음 처리할 때")
    class WhenOwnerMarksTheirOwnNotification {

        @Test
        void savesItWithAReadAt() {
            given(findNotificationPort.findById(command.getNotificationId()))
                    .willReturn(Optional.of(NotificationTestFixture.anUnreadNotification(notificationId, recipientId)));
            willAnswer(invocation -> invocation.getArgument(0))
                    .given(saveNotificationPort).saveNotification(any(Notification.class));

            Notification result = markNotificationReadService.markNotificationRead(command);

            then(saveNotificationPort).should().saveNotification(notificationCaptor.capture());
            assertThat(notificationCaptor.getValue().isRead()).isTrue();
            assertThat(result.isRead()).isTrue();
        }
    }

    @Nested
    @DisplayName("이미 읽은 알림을 다시 읽음 처리할 때")
    class WhenNotificationIsAlreadyRead {

        @Test
        void skipsTheSaveAndReturnsItUnchanged() {
            Notification alreadyRead = NotificationTestFixture.anUnreadNotification(notificationId, recipientId)
                    .markRead(LocalDateTime.now());
            given(findNotificationPort.findById(command.getNotificationId())).willReturn(Optional.of(alreadyRead));

            Notification result = markNotificationReadService.markNotificationRead(command);

            assertThat(result).isSameAs(alreadyRead);
            then(saveNotificationPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("알림이 존재하지 않을 때")
    class WhenNotificationDoesNotExist {

        @Test
        void throwsNotFound() {
            given(findNotificationPort.findById(command.getNotificationId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> markNotificationReadService.markNotificationRead(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(NotificationExceptionCase.NOTIFICATION_NOT_FOUND);
            then(saveNotificationPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("다른 사람의 알림을 읽음 처리하려 할 때")
    class WhenCallerIsNotTheRecipient {

        @Test
        void throwsNotFoundInsteadOfForbidden() {
            given(findNotificationPort.findById(command.getNotificationId()))
                    .willReturn(Optional.of(NotificationTestFixture.anUnreadNotification(notificationId, UUID.randomUUID())));

            Throwable thrown = catchThrowable(() -> markNotificationReadService.markNotificationRead(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(NotificationExceptionCase.NOTIFICATION_NOT_FOUND);
            then(saveNotificationPort).shouldHaveNoInteractions();
        }
    }
}
