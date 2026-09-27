package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.SessionId;

import java.util.Optional;

public interface FindSessionPort {
    /**
     * Returns the session's member when it exists and is within both timeouts; with {@code touch},
     * also restarts its idle timeout. Empty for a missing, expired, or unknown id alike. Throws
     * {@code SESSION_REPLACED} for one a later login of the same member took over.
     */
    Optional<MemberAuthData> findSession(SessionId sessionId, boolean touch);
}
