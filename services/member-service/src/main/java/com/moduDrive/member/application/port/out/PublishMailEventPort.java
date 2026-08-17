package com.moduDrive.member.application.port.out;

public interface PublishMailEventPort {
    void publishVerificationRequested(String email, String verificationCode);
}
