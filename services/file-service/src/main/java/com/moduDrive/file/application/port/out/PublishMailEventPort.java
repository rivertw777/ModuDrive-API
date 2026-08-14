package com.moduDrive.file.application.port.out;

import java.util.UUID;

public interface PublishMailEventPort {
    void publishShareInviteRequested(UUID fileId, String granteeEmail, String fileName, String role);
}
