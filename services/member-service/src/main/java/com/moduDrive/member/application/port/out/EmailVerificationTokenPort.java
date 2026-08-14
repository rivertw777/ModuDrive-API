package com.moduDrive.member.application.port.out;

import java.util.Optional;

public interface EmailVerificationTokenPort {

    void saveToken(String token, String email);

    /** Resolves and invalidates the token in one step so it can't be replayed. */
    Optional<String> consumeToken(String token);

    void markVerified(String email);

    /** Consumes the verified flag so a confirmed email can't be reused for a second sign-up. */
    boolean consumeVerified(String email);
}
