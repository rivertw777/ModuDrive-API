package com.moduDrive.notification.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataNotificationRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    boolean existsByEventId(UUID eventId);

    Page<NotificationJpaEntity> findByRecipientId(UUID recipientId, Pageable pageable);

    Page<NotificationJpaEntity> findByRecipientIdAndReadAtIsNull(UUID recipientId, Pageable pageable);
}
