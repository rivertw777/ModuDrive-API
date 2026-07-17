package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.UploadChunkCommand;

public interface UploadChunkUseCase {

    void uploadChunk(UploadChunkCommand command);
}
