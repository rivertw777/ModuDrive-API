package com.moduDrive.notification.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.notification.application.port.in.command.ListNotificationsCommand;
import com.moduDrive.notification.application.port.in.usecase.ListNotificationsUseCase;
import com.moduDrive.notification.application.port.in.usecase.NotificationPage;
import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.fixture.NotificationTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListNotificationsController.class)
@Import(GlobalExceptionHandler.class)
class ListNotificationsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ListNotificationsUseCase listNotificationsUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("수신자가 알림 목록을 조회할 때")
    class WhenRecipientLists {

        @Test
        void returnsAPageOfNotifications() throws Exception {
            UUID notificationId = UUID.randomUUID();
            Notification notification = NotificationTestFixture
                    .anUnreadNotification(notificationId, UUID.fromString(USER_ID));
            given(listNotificationsUseCase.listNotifications(any(ListNotificationsCommand.class)))
                    .willReturn(new NotificationPage(List.of(notification), 0, true, 1));

            mockMvc.perform(get("/api/v1/notifications").header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(notificationId.toString()))
                    .andExpect(jsonPath("$.data.content[0].fileName").value("report.pdf"))
                    .andExpect(jsonPath("$.data.content[0].role").value("EDITOR"))
                    .andExpect(jsonPath("$.data.content[0].sharerName").value("홍길동"))
                    .andExpect(jsonPath("$.data.content[0].sharerEmail").value("owner@modudrive.com"))
                    .andExpect(jsonPath("$.data.content[0].read").value(false))
                    .andExpect(jsonPath("$.data.number").value(0))
                    .andExpect(jsonPath("$.data.last").value(true))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("unreadOnly 쿼리 파라미터를 커맨드로 전달한다")
        void passesTheUnreadOnlyFlagIntoTheCommand() throws Exception {
            given(listNotificationsUseCase.listNotifications(any(ListNotificationsCommand.class)))
                    .willReturn(new NotificationPage(List.of(), 0, true, 0));

            mockMvc.perform(get("/api/v1/notifications")
                            .param("unreadOnly", "true")
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk());

            var captor = forClass(ListNotificationsCommand.class);
            then(listNotificationsUseCase).should().listNotifications(captor.capture());
            assertThat(captor.getValue().isUnreadOnly()).isTrue();
            assertThat(captor.getValue().getRecipientId().value()).isEqualTo(UUID.fromString(USER_ID));
        }
    }

    @Nested
    @DisplayName("페이지 파라미터가 범위를 벗어날 때")
    class WhenPageParamsAreOutOfRange {

        @Test
        void rejectsATooLargePageSize() throws Exception {
            mockMvc.perform(get("/api/v1/notifications")
                            .param("size", "101")
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isBadRequest());

            then(listNotificationsUseCase).shouldHaveNoInteractions();
        }
    }
}
