package com.moduDrive.notification.application.service;

import com.moduDrive.notification.application.port.in.command.ListNotificationsCommand;
import com.moduDrive.notification.application.port.in.usecase.NotificationPage;
import com.moduDrive.notification.application.port.out.FindNotificationPort;
import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.fixture.NotificationTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ListNotificationsServiceTest {

    @Mock
    private FindNotificationPort findNotificationPort;
    @InjectMocks
    private ListNotificationsService listNotificationsService;

    private final UUID recipientId = UUID.randomUUID();

    @Nested
    @DisplayName("전체 알림을 조회할 때")
    class WhenListingAll {

        @Test
        void delegatesToTheFindPortWithUnreadOnlyFalse() {
            ListNotificationsCommand command = new ListNotificationsCommand(recipientId, false, 0, 20);
            Notification notification = NotificationTestFixture.anUnreadNotification(UUID.randomUUID(), recipientId);
            given(findNotificationPort.findByRecipientId(command.getRecipientId(), false, 0, 20))
                    .willReturn(new NotificationPage(List.of(notification), 0, true, 1));

            NotificationPage result = listNotificationsService.listNotifications(command);

            assertThat(result.content()).containsExactly(notification);
            then(findNotificationPort).should().findByRecipientId(command.getRecipientId(), false, 0, 20);
        }
    }

    @Nested
    @DisplayName("안 읽은 알림만 조회할 때")
    class WhenListingUnreadOnly {

        @Test
        void passesTheUnreadOnlyFlagThrough() {
            ListNotificationsCommand command = new ListNotificationsCommand(recipientId, true, 0, 20);
            given(findNotificationPort.findByRecipientId(command.getRecipientId(), true, 0, 20))
                    .willReturn(new NotificationPage(List.of(), 0, true, 0));

            NotificationPage result = listNotificationsService.listNotifications(command);

            assertThat(result.content()).isEmpty();
            then(findNotificationPort).should().findByRecipientId(command.getRecipientId(), true, 0, 20);
        }
    }
}
