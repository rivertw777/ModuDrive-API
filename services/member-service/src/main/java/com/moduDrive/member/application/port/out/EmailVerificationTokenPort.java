package com.moduDrive.member.application.port.out;

public interface EmailVerificationTokenPort {

    /** Counts one code request for the address, unless it's within the resend cooldown (not counted). */
    CodeRequest requestCode(String email);

    void saveCode(String email, String code);

    /**
     * Matches the code stored for the email and invalidates it in one step so it can't be replayed.
     * Once the code can't be used (matched, expired, or out of attempts) the resend cooldown ends with it.
     */
    CodeConfirmation confirmCode(String email, String code);

    void markVerified(String email);

    /** Consumes the verified flag so a confirmed email can't be reused for a second sign-up. */
    boolean consumeVerified(String email);

    enum CodeRequest {
        ALLOWED,
        /** Within the resend cooldown after the address's last code. */
        TOO_SOON,
        /** The address has asked too often in the window. */
        TOO_MANY
    }

    enum CodeConfirmation {
        MATCHED,
        /** Wrong, but the same code can still be retried. */
        MISMATCHED,
        /** No code — expired, or never sent. */
        EXPIRED,
        /** The wrong codes are used up — by this guess or an earlier one. */
        EXHAUSTED
    }
}
