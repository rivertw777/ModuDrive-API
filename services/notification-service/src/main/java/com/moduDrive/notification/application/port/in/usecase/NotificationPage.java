package com.moduDrive.notification.application.port.in.usecase;

import com.moduDrive.notification.domain.model.Notification;

import java.util.List;

/** One page of a recipient's feed, newest first. {@code last} marks the final page. */
public record NotificationPage(List<Notification> content, int number, boolean last, long totalElements) {
}
