package com.moduDrive.notification.adapter.in.web.dto;

import com.moduDrive.notification.application.port.in.usecase.NotificationPage;

import java.util.List;

/** The web client reads these field names; they match Spring Data's {@code Page} JSON. */
public record NotificationPageResponse(
        List<NotificationResponse> content,
        int number,
        boolean last,
        long totalElements
) {
    public static NotificationPageResponse from(NotificationPage page) {
        return new NotificationPageResponse(
                page.content().stream().map(NotificationResponse::from).toList(),
                page.number(), page.last(), page.totalElements());
    }
}
