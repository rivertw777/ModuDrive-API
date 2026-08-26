package com.moduDrive.notification.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.notification.application.port.out.FindNotificationPort;
import com.moduDrive.notification.application.port.out.SaveNotificationPort;
import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.domain.model.Notification.NotificationEventId;
import com.moduDrive.notification.domain.model.Notification.NotificationId;
import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import com.moduDrive.notification.exception.NotificationExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@PersistenceAdapter
class NotificationPersistenceAdapter implements SaveNotificationPort, FindNotificationPort {

    private final SpringDataNotificationRepository springDataNotificationRepository;
    private final NotificationMapper notificationMapper;

    /** REQUIRES_NEW, and the {@code DataIntegrityViolationException} from a concurrent duplicate
     * {@code event_id} is deliberately left uncaught here: once a flush fails, Hibernate marks
     * that transaction unable to commit, so catching the exception inside the same transactional
     * boundary would just move the failure to this method's own commit. Letting it propagate out
     * of this REQUIRES_NEW proxy makes Spring roll back only this isolated insert — the caller's
     * transaction was suspended, never touched the failing flush, and can safely catch it. See
     * {@link com.moduDrive.notification.application.service.RecordFileSharedNotificationService}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Notification insertNotification(Notification notification) {
        return notificationMapper.mapToDomain(
                springDataNotificationRepository.saveAndFlush(notificationMapper.mapToEntity(notification)));
    }

    /** Updates the row in place rather than mapping to a detached entity, so mutable fields
     * (currently just {@code readAt}) change without touching {@code id}/{@code eventId}/
     * {@code createdAt}. */
    @Override
    public Notification saveNotification(Notification notification) {
        NotificationJpaEntity entity = springDataNotificationRepository.findById(notification.getId())
                .orElseThrow(() -> new BusinessException(NotificationExceptionCase.NOTIFICATION_NOT_FOUND));
        entity.applyReadAt(notification.getReadAt());
        return notificationMapper.mapToDomain(springDataNotificationRepository.save(entity));
    }

    @Override
    public boolean existsByEventId(NotificationEventId eventId) {
        return springDataNotificationRepository.existsByEventId(eventId.value());
    }

    @Override
    public Optional<Notification> findById(NotificationId id) {
        return springDataNotificationRepository.findById(id.value())
                .map(notificationMapper::mapToDomain);
    }

    @Override
    public Page<Notification> findByRecipientId(NotificationRecipientId recipientId, boolean unreadOnly, Pageable pageable) {
        Page<NotificationJpaEntity> page = unreadOnly
                ? springDataNotificationRepository.findByRecipientIdAndReadAtIsNull(recipientId.value(), pageable)
                : springDataNotificationRepository.findByRecipientId(recipientId.value(), pageable);
        return page.map(notificationMapper::mapToDomain);
    }
}
