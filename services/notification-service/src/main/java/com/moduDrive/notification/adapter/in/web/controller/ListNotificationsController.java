package com.moduDrive.notification.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.notification.adapter.in.web.dto.NotificationPageResponse;
import com.moduDrive.notification.application.port.in.command.ListNotificationsCommand;
import com.moduDrive.notification.application.port.in.usecase.ListNotificationsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@WebAdapter
@RestController
@RequiredArgsConstructor
class ListNotificationsController {

    private final ListNotificationsUseCase listNotificationsUseCase;

    @GetMapping("/api/v1/notifications")
    public ApiResponse<NotificationPageResponse> listNotifications(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        val command = new ListNotificationsCommand(userId, unreadOnly, page, size);
        return ApiResponse.success(NotificationPageResponse.from(listNotificationsUseCase.listNotifications(command)));
    }
}
