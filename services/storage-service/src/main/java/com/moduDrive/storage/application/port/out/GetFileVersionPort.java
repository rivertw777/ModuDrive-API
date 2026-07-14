package com.moduDrive.storage.application.port.out;

import java.util.UUID;

public interface GetFileVersionPort {

    String getS3Path(UUID fileId);

    int getBlockCount(UUID fileId);
}
