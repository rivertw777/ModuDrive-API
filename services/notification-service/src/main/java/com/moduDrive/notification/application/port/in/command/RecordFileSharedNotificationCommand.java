package com.moduDrive.notification.application.port.in.command;

import com.moduDrive.notification.domain.model.Notification.NotificationDirectory;
import com.moduDrive.notification.domain.model.Notification.NotificationEventId;
import com.moduDrive.notification.domain.model.Notification.NotificationFileId;
import com.moduDrive.notification.domain.model.Notification.NotificationFileName;
import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import com.moduDrive.notification.domain.model.Notification.NotificationRole;
import com.moduDrive.notification.domain.model.Notification.NotificationSharerEmail;
import com.moduDrive.notification.domain.model.Notification.NotificationSharerName;
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
    private final NotificationDirectory directory;
    private final NotificationSharerName sharerName;
    private final NotificationSharerEmail sharerEmail;

    public RecordFileSharedNotificationCommand(UUID eventId, UUID recipientId, UUID fileId, String fileName,
                                               String role, boolean directory, String sharerName, String sharerEmail) {
        this.eventId = new NotificationEventId(eventId);
        this.recipientId = new NotificationRecipientId(recipientId);
        this.fileId = new NotificationFileId(fileId);
        this.fileName = new NotificationFileName(fileName);
        this.role = new NotificationRole(role);
        this.directory = new NotificationDirectory(directory);
        this.sharerName = new NotificationSharerName(sharerName);
        this.sharerEmail = new NotificationSharerEmail(sharerEmail);
    }
}
