package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.PurgeBlocksCommand;

public interface PurgeBlocksUseCase {

    void purgeBlocks(PurgeBlocksCommand command);
}
