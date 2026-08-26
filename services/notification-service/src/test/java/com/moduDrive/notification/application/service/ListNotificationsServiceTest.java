package com.moduDrive.notification.application.service;

import com.moduDrive.notification.application.port.in.command.ListNotificationsCommand;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    private final Pageable pageable = PageRequest.of(0, 20);

    @Nested
    @DisplayName("전체 알림을 조회할 때")
    class WhenListingAll {

        @Test
        void delegatesToTheFindPortWithUnreadOnlyFalse() {
            ListNotificationsCommand command = new ListNotificationsCommand(recipientId, false, pageable);
            Notification notification = NotificationTestFixture.anUnreadNotification(UUID.randomUUID(), recipientId);
            given(findNotificationPort.findByRecipientId(command.getRecipientId(), false, pageable))
                    .willReturn(new PageImpl<>(List.of(notification), pageable, 1));

            Page<Notification> result = listNotificationsService.listNotifications(command);

            assertThat(result.getContent()).containsExactly(notification);
            then(findNotificationPort).should().findByRecipientId(command.getRecipientId(), false, pageable);
        }
    }

    @Nested
    @DisplayName("안 읽은 알림만 조회할 때")
    class WhenListingUnreadOnly {

        @Test
        void passesTheUnreadOnlyFlagThrough() {
            ListNotificationsCommand command = new ListNotificationsCommand(recipientId, true, pageable);
            given(findNotificationPort.findByRecipientId(command.getRecipientId(), true, pageable))
                    .willReturn(Page.empty(pageable));

            Page<Notification> result = listNotificationsService.listNotifications(command);

            assertThat(result).isEmpty();
            then(findNotificationPort).should().findByRecipientId(command.getRecipientId(), true, pageable);
        }
    }
}
