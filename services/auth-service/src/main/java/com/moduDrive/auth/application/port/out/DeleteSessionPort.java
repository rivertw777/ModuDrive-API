package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.vo.SessionId;

public interface DeleteSessionPort {
    void deleteSession(SessionId sessionId);
}
