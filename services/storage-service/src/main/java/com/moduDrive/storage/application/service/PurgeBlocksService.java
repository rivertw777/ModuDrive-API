package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.command.PurgeBlocksCommand;
import com.moduDrive.storage.application.port.in.usecase.PurgeBlocksUseCase;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import com.moduDrive.storage.application.port.out.FindScheduledUploadsPort;
import com.moduDrive.storage.domain.model.Blocks;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.Set;

/** Blocks file-service stopped referencing a day ago. One written after file-service decided is kept
 * — see {@link DeleteBlocksPort}. So is one whose upload URL was signed within {@link Blocks#UPLOAD_TTL},
 * even if the object is older: a commit takes such a block (spec 001 2-3), and re-uploading a block
 * already in S3 doesn't rewrite it, so deleting the object would commit a version whose block is gone.
 * The uncommitted-upload sweep deletes it later if nothing commits it. */
@UseCase
@RequiredArgsConstructor
class PurgeBlocksService implements PurgeBlocksUseCase {

    private final DeleteBlocksPort deleteBlocksPort;
    private final FindScheduledUploadsPort findScheduledUploadsPort;

    @Override
    public void purgeBlocks(PurgeBlocksCommand command) {
        Set<String> live = findScheduledUploadsPort.findScheduledSince(
                command.getOwnerId(), command.getHashes(), Instant.now().minus(Blocks.UPLOAD_TTL));
        command.getHashes().stream()
                .filter(hash -> !live.contains(hash))
                .forEach(hash -> deleteBlocksPort.deleteUnlessRewritten(
                        Blocks.key(command.getOwnerId(), hash), command.getDecidedAt()));
    }
}
