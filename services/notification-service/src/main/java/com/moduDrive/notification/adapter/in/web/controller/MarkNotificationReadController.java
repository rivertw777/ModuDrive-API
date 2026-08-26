package com.moduDrive.notification.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.notification.adapter.in.web.dto.NotificationResponse;
import com.moduDrive.notification.application.port.in.command.MarkNotificationReadCommand;
import com.moduDrive.notification.application.port.in.usecase.MarkNotificationReadUseCase;
import com.moduDrive.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class MarkNotificationReadController {

    private final MarkNotificationReadUseCase markNotificationReadUseCase;

    @PatchMapping("/api/v1/notifications/{notificationId}/read")
    public ApiResponse<NotificationResponse> markNotificationRead(
            @RequestHeader("X_USER_ID") UUID userId,
            @PathVariable UUID notificationId) {
        Notification notification = markNotificationReadUseCase.markNotificationRead(
                new MarkNotificationReadCommand(notificationId, userId));
        return ApiResponse.success(NotificationResponse.from(notification));
    }
}
