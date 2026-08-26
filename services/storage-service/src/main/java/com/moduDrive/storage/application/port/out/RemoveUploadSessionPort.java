package com.moduDrive.storage.application.port.out;

import java.util.UUID;

public interface RemoveUploadSessionPort {

    void removeSession(UUID sessionId);
}
