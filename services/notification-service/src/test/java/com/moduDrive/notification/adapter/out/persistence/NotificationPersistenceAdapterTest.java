package com.moduDrive.notification.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.config.AuditingConfig;
import com.moduDrive.notification.domain.model.Notification;
import com.moduDrive.notification.domain.model.Notification.NotificationEventId;
import com.moduDrive.notification.domain.model.Notification.NotificationFileId;
import com.moduDrive.notification.domain.model.Notification.NotificationFileName;
import com.moduDrive.notification.domain.model.Notification.NotificationId;
import com.moduDrive.notification.domain.model.Notification.NotificationRecipientId;
import com.moduDrive.notification.domain.model.Notification.NotificationRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
// AuditingConfig is a third-party auto-configuration, so the @DataJpaTest slice drops it —
// without it @CreatedDate never fires and createdAt reads back null, unlike production.
@Import({NotificationPersistenceAdapter.class, NotificationMapper.class, AuditingConfig.class})
class NotificationPersistenceAdapterTest {

    @Autowired
    private NotificationPersistenceAdapter notificationPersistenceAdapter;

    private final UUID recipientId = UUID.randomUUID();

    private Notification save(UUID recipientId) {
        return notificationPersistenceAdapter.insertNotification(newNotification(recipientId, UUID.randomUUID()));
    }

    private Notification newNotification(UUID recipientId, UUID eventId) {
        return Notification.create(
                new NotificationEventId(eventId),
                new NotificationRecipientId(recipientId),
                new NotificationFileId(UUID.randomUUID()),
                new NotificationFileName("report.pdf"),
                new NotificationRole("EDITOR"));
    }

    @Nested
    @DisplayName("새 알림을 저장할 때")
    class WhenSavingANewNotification {

        @Test
        void assignsAnIdAndCreatedAtAndStaysUnread() {
            Notification saved = save(recipientId);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.isRead()).isFalse();
            assertThat(saved.getFileName()).isEqualTo("report.pdf");
        }

        @Test
        @DisplayName("같은 event_id로 두 번 저장하면 유니크 제약에 걸린다")
        void rejectsADuplicateEventId() {
            UUID eventId = UUID.randomUUID();
            notificationPersistenceAdapter.insertNotification(newNotification(recipientId, eventId));

            Throwable thrown = catchThrowable(() ->
                    notificationPersistenceAdapter.insertNotification(newNotification(recipientId, eventId)));

            assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("event_id 존재 여부를 확인할 때")
    class WhenCheckingEventIdExistence {

        @Test
        void reportsTrueOnlyForARecordedEvent() {
            Notification saved = save(recipientId);

            assertThat(notificationPersistenceAdapter.existsByEventId(new NotificationEventId(saved.getEventId()))).isTrue();
            assertThat(notificationPersistenceAdapter.existsByEventId(new NotificationEventId(UUID.randomUUID()))).isFalse();
        }
    }

    @Nested
    @DisplayName("수신자별로 알림을 조회할 때")
    class WhenFindingByRecipient {

        @Test
        void returnsOnlyThatRecipientsNotifications() {
            save(recipientId);
            save(recipientId);
            save(UUID.randomUUID());

            Page<Notification> page = notificationPersistenceAdapter.findByRecipientId(
                    new NotificationRecipientId(recipientId), false, PageRequest.of(0, 20));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).allMatch(n -> n.getRecipientId().equals(recipientId));
        }

        @Test
        @DisplayName("unreadOnly면 읽은 알림은 제외한다")
        void excludesReadNotificationsWhenUnreadOnly() {
            Notification read = save(recipientId);
            save(recipientId);
            notificationPersistenceAdapter.saveNotification(read.markRead(LocalDateTime.now()));

            Page<Notification> page = notificationPersistenceAdapter.findByRecipientId(
                    new NotificationRecipientId(recipientId), true, PageRequest.of(0, 20));

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent()).allMatch(n -> !n.isRead());
        }

        @Test
        void appliesThePageSize() {
            save(recipientId);
            save(recipientId);
            save(recipientId);

            Page<Notification> page = notificationPersistenceAdapter.findByRecipientId(
                    new NotificationRecipientId(recipientId), false, PageRequest.of(0, 2));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(3);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("기존 알림을 읽음 처리해 저장할 때")
    class WhenSavingAnExistingNotificationAsRead {

        @Test
        void updatesTheRowInPlaceKeepingIdEventIdAndCreatedAt() {
            Notification saved = save(recipientId);
            LocalDateTime readAt = LocalDateTime.now();

            Notification updated = notificationPersistenceAdapter.saveNotification(saved.markRead(readAt));

            assertThat(updated.getId()).isEqualTo(saved.getId());
            assertThat(updated.getEventId()).isEqualTo(saved.getEventId());
            // insertNotification runs in its own REQUIRES_NEW transaction (see the adapter), so
            // `saved.getCreatedAt()` is the raw in-memory, nanosecond-precision value the
            // @CreatedDate listener set — while `updated` came back via a genuine SELECT in this
            // test's own transaction, at the column's stored precision, which can round rather
            // than truncate the in-memory value. A tolerance is correct here, not truncation on
            // both sides: truncating the expected value can still land a microsecond below a
            // value the column rounded up.
            assertThat(updated.getCreatedAt()).isCloseTo(saved.getCreatedAt(), within(1, ChronoUnit.MILLIS));
            assertThat(updated.isRead()).isTrue();

            Notification reloaded = notificationPersistenceAdapter.findById(new NotificationId(saved.getId())).orElseThrow();
            assertThat(reloaded.isRead()).isTrue();
        }
    }

    @Nested
    @DisplayName("id로 알림을 조회할 때")
    class WhenFindingById {

        @Test
        void returnsEmptyForAnUnknownId() {
            assertThat(notificationPersistenceAdapter.findById(new NotificationId(UUID.randomUUID()))).isEmpty();
        }
    }
}
