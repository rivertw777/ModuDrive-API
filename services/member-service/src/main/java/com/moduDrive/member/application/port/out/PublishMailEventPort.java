package com.moduDrive.member.application.port.out;

import java.util.UUID;

public interface PublishMailEventPort {
    void publishVerificationRequested(UUID memberId, String email, String name, String verificationToken);
}
