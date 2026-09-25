package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.vo.SessionId;

public interface CreateSessionPort {
    /** Stores a new session for the member and returns its freshly generated id. */
    SessionId createSession(MemberAuthData memberAuthData);
}
