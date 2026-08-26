package com.moduDrive.notification.application.port.in.command;

import com.moduDrive.notification.domain.model.Notification.NotificationEventId;
import com.moduDrive.notification.domain.model.Notification.NotificationFileId;
import com.moduDrive.notification.domain.model.Notification.NotificationFileName;
import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import com.moduDrive.notification.domain.model.Notification.NotificationRole;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode
public class RecordFileSharedNotificationCommand {

    private final NotificationEventId eventId;
    private final NotificationRecipientId recipientId;
    private final NotificationFileId fileId;
    private final NotificationFileName fileName;
    private final NotificationRole role;

    public RecordFileSharedNotificationCommand(UUID eventId, UUID recipientId, UUID fileId, String fileName, String role) {
        this.eventId = new NotificationEventId(eventId);
        this.recipientId = new NotificationRecipientId(recipientId);
        this.fileId = new NotificationFileId(fileId);
        this.fileName = new NotificationFileName(fileName);
        this.role = new NotificationRole(role);
    }
}
