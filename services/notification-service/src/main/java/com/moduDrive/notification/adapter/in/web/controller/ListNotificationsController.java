package com.moduDrive.notification.adapter.in.web.controller;

import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.notification.adapter.in.web.dto.NotificationResponse;
import com.moduDrive.notification.application.port.in.command.ListNotificationsCommand;
import com.moduDrive.notification.application.port.in.usecase.ListNotificationsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    public ApiResponse<Page<NotificationResponse>> listNotifications(
            @RequestHeader("X_USER_ID") UUID userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<NotificationResponse> notifications = listNotificationsUseCase
                .listNotifications(new ListNotificationsCommand(userId, unreadOnly, pageable))
                .map(NotificationResponse::from);
        return ApiResponse.success(notifications);
    }
}
