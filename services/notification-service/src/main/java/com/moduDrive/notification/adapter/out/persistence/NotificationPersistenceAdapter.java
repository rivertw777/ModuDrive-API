package com.moduDrive.notification.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.notification.application.port.in.usecase.NotificationPage;
import com.moduDrive.notification.application.port.out.FindNotificationPort;
import com.moduDrive.notification.application.port.out.SaveNotificationPort;
import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.domain.model.Notification.NotificationEventId;
import com.moduDrive.notification.domain.model.Notification.NotificationId;
import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import com.moduDrive.notification.exception.NotificationExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@PersistenceAdapter
class NotificationPersistenceAdapter implements SaveNotificationPort, FindNotificationPort {

    private final SpringDataNotificationRepository springDataNotificationRepository;
    private final NotificationMapper notificationMapper;

    /** REQUIRES_NEW: a failed flush marks its transaction rollback-only, so a duplicate {@code event_id}
     * must fail in its own transaction for the caller to be able to catch it and still commit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Notification insertNotification(Notification notification) {
        return notificationMapper.mapToDomain(
                springDataNotificationRepository.saveAndFlush(notificationMapper.mapToEntity(notification)));
    }

    /** Updates the loaded row in place so {@code createdAt} and the other immutable columns are kept. */
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
    public NotificationPage findByRecipientId(NotificationRecipientId recipientId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationJpaEntity> result = unreadOnly
                ? springDataNotificationRepository.findByRecipientIdAndReadAtIsNull(recipientId.value(), pageable)
                : springDataNotificationRepository.findByRecipientId(recipientId.value(), pageable);
        return new NotificationPage(
                result.map(notificationMapper::mapToDomain).getContent(),
                result.getNumber(), result.isLast(), result.getTotalElements());
    }
}
