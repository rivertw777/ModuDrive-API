package com.moduDrive.storage.application.port.out;

import com.moduDrive.storage.domain.model.UploadSession;

import java.util.Optional;
import java.util.UUID;

public interface FindUploadSessionPort {

    Optional<UploadSession> findSession(UUID sessionId);
}
