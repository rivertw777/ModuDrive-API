package com.moduDrive.notification.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.notification.application.port.in.command.MarkNotificationReadCommand;
import com.moduDrive.notification.application.port.in.usecase.MarkNotificationReadUseCase;
import com.moduDrive.notification.exception.NotificationExceptionCase;
import com.moduDrive.notification.fixture.NotificationTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarkNotificationReadController.class)
@Import(GlobalExceptionHandler.class)
class MarkNotificationReadControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MarkNotificationReadUseCase markNotificationReadUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID NOTIFICATION_ID = UUID.randomUUID();

    @Nested
    @DisplayName("수신자가 자신의 알림을 읽음 처리할 때")
    class WhenRecipientMarksRead {

        @Test
        void returnsTheReadNotification() throws Exception {
            given(markNotificationReadUseCase.markNotificationRead(any(MarkNotificationReadCommand.class)))
                    .willReturn(NotificationTestFixture.aReadNotification(
                            NOTIFICATION_ID, UUID.fromString(USER_ID), LocalDateTime.of(2026, 8, 25, 11, 0)));

            mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", NOTIFICATION_ID)
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(NOTIFICATION_ID.toString()))
                    .andExpect(jsonPath("$.data.read").value(true));
        }
    }

    @Nested
    @DisplayName("본인 알림이 아니거나 존재하지 않을 때")
    class WhenNotFoundOrNotOwned {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(NotificationExceptionCase.NOTIFICATION_NOT_FOUND))
                    .given(markNotificationReadUseCase).markNotificationRead(any(MarkNotificationReadCommand.class));

            mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", NOTIFICATION_ID)
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isNotFound());
        }
    }
}
