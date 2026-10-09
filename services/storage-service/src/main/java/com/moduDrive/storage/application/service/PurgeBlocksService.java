package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PurgeBlocksCommand;
import com.moduDrive.storage.application.port.in.usecase.PurgeBlocksUseCase;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import com.moduDrive.storage.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.storage.domain.model.Blocks;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/** Blocks file-service stopped referencing a day ago. One that was uploaded again after file-service
 * decided (the same bytes, a new upload) is kept — see {@link DeleteBlocksPort}. So is one with a
 * live upload record, even if it was written before the decision: a commit trusts that record, so
 * deleting the object under it would commit a version whose block is gone. The uncommitted-upload
 * sweep deletes it later if nothing commits it. */
@UseCase
@RequiredArgsConstructor
class PurgeBlocksService implements PurgeBlocksUseCase {

    private final DeleteBlocksPort deleteBlocksPort;
    private final FindUploadedBlocksPort findUploadedBlocksPort;

    @Override
    public void purgeBlocks(PurgeBlocksCommand command) {
        Set<String> uploaded = findUploadedBlocksPort.findUploaded(command.getOwnerId(), command.getHashes()).keySet();
        command.getHashes().stream()
                .filter(hash -> !uploaded.contains(hash))
                .forEach(hash -> deleteBlocksPort.deleteUnlessRewritten(
                        Blocks.key(command.getOwnerId(), hash), command.getDecidedAt()));
    }
}
