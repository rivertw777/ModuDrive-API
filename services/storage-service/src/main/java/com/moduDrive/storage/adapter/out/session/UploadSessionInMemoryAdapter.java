package com.moduDrive.storage.adapter.out.session;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.storage.application.port.out.CreateUploadSessionPort;
import com.moduDrive.storage.application.port.out.FindUploadSessionPort;
import com.moduDrive.storage.domain.model.UploadSession;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@PersistenceAdapter
class UploadSessionInMemoryAdapter implements CreateUploadSessionPort, FindUploadSessionPort {

    private final ConcurrentHashMap<UUID, UploadSession> store = new ConcurrentHashMap<>();

    @Override
    public void createSession(UploadSession session) {
        store.put(session.getSessionId(), session);
    }

    @Override
    public Optional<UploadSession> findSession(UUID sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }
}
