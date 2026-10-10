package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.UploadBlocksCommand;

public interface UploadBlocksUseCase {

    void uploadBlocks(UploadBlocksCommand command);
}
