package com.moduDrive.storage.application.port.out;

import com.moduDrive.storage.domain.model.UploadSession;

public interface CreateUploadSessionPort {

    void createSession(UploadSession session);
}
