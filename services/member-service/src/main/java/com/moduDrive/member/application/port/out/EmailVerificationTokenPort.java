package com.moduDrive.member.application.port.out;

public interface EmailVerificationTokenPort {

    void saveCode(String email, String code);

    /** Matches the code stored for the email and invalidates it in one step so it can't be replayed. */
    boolean confirmCode(String email, String code);

    void markVerified(String email);

    /** Consumes the verified flag so a confirmed email can't be reused for a second sign-up. */
    boolean consumeVerified(String email);
}
