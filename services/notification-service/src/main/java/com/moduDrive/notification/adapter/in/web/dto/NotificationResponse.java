package com.moduDrive.notification.adapter.in.web.dto;

import com.moduDrive.notification.domain.model.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID fileId,
        String fileName,
        String role,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(), notification.getFileId(), notification.getFileName(),
                notification.getRole(), notification.isRead(), notification.getCreatedAt());
    }
}
