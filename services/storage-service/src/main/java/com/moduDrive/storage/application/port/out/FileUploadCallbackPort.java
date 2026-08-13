package com.moduDrive.storage.application.port.out;

import java.util.UUID;

public interface FileUploadCallbackPort {

    void notifyUploadComplete(UUID fileId, UUID userId, long fileSize, int blockCount, String s3Path);
}
