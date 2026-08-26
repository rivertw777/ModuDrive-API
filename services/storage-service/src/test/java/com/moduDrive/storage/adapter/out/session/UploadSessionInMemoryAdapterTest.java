package com.moduDrive.storage.adapter.out.session;

import com.moduDrive.storage.domain.model.UploadSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UploadSessionInMemoryAdapterTest {

    @Nested
    @DisplayName("세션을 명시적으로 제거할 때")
    class WhenRemovingASession {

        @Test
        void isNoLongerFindable() {
            UploadSessionInMemoryAdapter adapter = new UploadSessionInMemoryAdapter(24);
            UploadSession session = UploadSession.create(UUID.randomUUID(), UUID.randomUUID(), 1);
            adapter.createSession(session);

            adapter.removeSession(session.getSessionId());

            assertThat(adapter.findSession(session.getSessionId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("TTL을 초과한 세션을 정리할 때")
    class WhenSweepingExpiredSessions {

        @Test
        void evictsSessionsOlderThanTheTtl() {
            // TTL 0 makes every already-created session older than the sweep's cutoff, without
            // needing to sleep or backdate a timestamp.
            UploadSessionInMemoryAdapter adapter = new UploadSessionInMemoryAdapter(0);
            UploadSession session = UploadSession.create(UUID.randomUUID(), UUID.randomUUID(), 1);
            adapter.createSession(session);

            adapter.evictExpiredSessions();

            assertThat(adapter.findSession(session.getSessionId())).isEmpty();
        }

        @Test
        void keepsSessionsWithinTheTtl() {
            UploadSessionInMemoryAdapter adapter = new UploadSessionInMemoryAdapter(24);
            UploadSession session = UploadSession.create(UUID.randomUUID(), UUID.randomUUID(), 1);
            adapter.createSession(session);

            adapter.evictExpiredSessions();

            assertThat(adapter.findSession(session.getSessionId())).isPresent();
        }
    }
}
