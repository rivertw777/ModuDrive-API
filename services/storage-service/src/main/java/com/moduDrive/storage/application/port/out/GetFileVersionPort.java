package com.moduDrive.storage.application.port.out;

import java.util.UUID;

public interface GetFileVersionPort {

    String getS3Path(UUID fileId, UUID userId);

    int getBlockCount(UUID fileId, UUID userId);
}
