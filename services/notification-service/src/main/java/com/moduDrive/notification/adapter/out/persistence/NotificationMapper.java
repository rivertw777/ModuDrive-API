package com.moduDrive.notification.adapter.out.persistence;

import com.moduDrive.notification.domain.model.Notification;
import org.springframework.stereotype.Component;

import static com.moduDrive.notification.domain.model.Notification.NotificationEventId;
import static com.moduDrive.notification.domain.model.Notification.NotificationFileId;
import static com.moduDrive.notification.domain.model.Notification.NotificationFileName;
import static com.moduDrive.notification.domain.model.Notification.NotificationId;
import static com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import static com.moduDrive.notification.domain.model.Notification.NotificationRole;

@Component
class NotificationMapper {

    Notification mapToDomain(NotificationJpaEntity entity) {
        return Notification.withId(
                new NotificationId(entity.getId()),
                new NotificationEventId(entity.getEventId()),
                new NotificationRecipientId(entity.getRecipientId()),
                new NotificationFileId(entity.getFileId()),
                new NotificationFileName(entity.getFileName()),
                new NotificationRole(entity.getRole()),
                entity.getReadAt(),
                entity.getCreatedAt()
        );
    }

    NotificationJpaEntity mapToEntity(Notification notification) {
        return new NotificationJpaEntity(
                notification.getEventId(), notification.getRecipientId(),
                notification.getFileId(), notification.getFileName(), notification.getRole());
    }
}
