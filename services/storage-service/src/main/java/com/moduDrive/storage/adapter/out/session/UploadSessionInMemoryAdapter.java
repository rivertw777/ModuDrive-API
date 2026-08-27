package com.moduDrive.storage.adapter.out.session;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.storage.application.port.out.CreateUploadSessionPort;
import com.moduDrive.storage.application.port.out.FindUploadSessionPort;
import com.moduDrive.storage.application.port.out.RemoveUploadSessionPort;
import com.moduDrive.storage.domain.model.UploadSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@PersistenceAdapter
class UploadSessionInMemoryAdapter implements CreateUploadSessionPort, FindUploadSessionPort, RemoveUploadSessionPort {

    private static final Logger logger = LoggerFactory.getLogger(UploadSessionInMemoryAdapter.class);

    private final ConcurrentHashMap<UUID, UploadSession> store = new ConcurrentHashMap<>();
    private final Duration sessionTtl;

    UploadSessionInMemoryAdapter(@Value("${modudrive.storage.upload-session-ttl-hours:24}") long ttlHours) {
        this.sessionTtl = Duration.ofHours(ttlHours);
    }

    @Override
    public void createSession(UploadSession session) {
        store.put(session.getSessionId(), session);
    }

    @Override
    public Optional<UploadSession> findSession(UUID sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    @Override
    public void removeSession(UUID sessionId) {
        store.remove(sessionId);
    }

    /** Client abandons a resumable upload (never calls /complete) and the session — with every
     * uploaded chunk still held as byte[] — sits in {@code store} forever. Sweeps out anything
     * older than {@code sessionTtl}, completed or not (#213). */
    @Scheduled(fixedDelay = 60 * 60 * 1000) // hourly
    void evictExpiredSessions() {
        Instant cutoff = Instant.now().minus(sessionTtl);
        int before = store.size();
        store.values().removeIf(session -> session.getCreatedAt().isBefore(cutoff));
        int removed = before - store.size();
        if (removed > 0) {
            logger.info("Evicted {} expired upload session(s)", removed);
        }
    }
}
