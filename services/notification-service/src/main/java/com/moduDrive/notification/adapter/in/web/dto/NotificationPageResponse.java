package com.moduDrive.notification.adapter.in.web.dto;

import com.moduDrive.notification.application.port.in.usecase.NotificationPage;

import java.util.List;

/** Keeps the field names of the Spring Data page this endpoint used to return, which the web
 * client reads ({@code content}, {@code number}, {@code last}, {@code totalElements}). */
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
