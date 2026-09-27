package com.moduDrive.member.application.port.out;

import java.util.UUID;

public interface PublishMemberEventPort {
    void publishSignedUp(UUID memberId, String email);

    void publishPasswordChanged(UUID memberId);
}
