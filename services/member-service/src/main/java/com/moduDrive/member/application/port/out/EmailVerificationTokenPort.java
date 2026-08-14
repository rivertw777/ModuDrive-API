package com.moduDrive.member.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenPort {

    void saveToken(String token, UUID memberId);

    /** Resolves and invalidates the token in one step so it can't be replayed. */
    Optional<UUID> consumeToken(String token);
}
