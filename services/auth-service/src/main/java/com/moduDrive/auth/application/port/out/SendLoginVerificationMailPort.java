package com.moduDrive.auth.application.port.out;

public interface SendLoginVerificationMailPort {
    /** Has the code mailed to the member's own address. */
    void sendLoginVerificationMail(String memberId, String code);
}
