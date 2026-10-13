package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.usecase.FindUploadedBlocksUseCase;
import com.moduDrive.storage.application.port.out.FindScheduledUploadsPort;
import com.moduDrive.storage.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.storage.domain.model.Blocks;
import com.moduDrive.storage.exception.StorageExceptionCase;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Spec 001 5: a block counts as uploaded if its upload URL was signed within {@link Blocks#UPLOAD_TTL}
 * and S3 holds it intact. Freshness comes from the signing, not the object's age: a block already in
 * S3 refuses a second PUT (If-None-Match), so re-uploading it only signs a URL again — and that
 * keeps the sweep off it just as long. Hashes never signed for aren't asked of S3 at all. */
@UseCase
@RequiredArgsConstructor
class FindUploadedBlocksService implements FindUploadedBlocksUseCase {

    private final FindScheduledUploadsPort findScheduledUploadsPort;
    private final FindUploadedBlocksPort findUploadedBlocksPort;

    @Override
    public Map<String, Integer> findUploadedBlocks(UUID ownerId, List<String> hashes) {
        if (!hashes.stream().allMatch(Blocks::isHash)) {
            throw new BusinessException(StorageExceptionCase.INVALID_BLOCK);
        }
        Set<String> fresh = findScheduledUploadsPort.findScheduledSince(ownerId, hashes, Instant.now().minus(Blocks.UPLOAD_TTL));
        return fresh.isEmpty() ? Map.of() : findUploadedBlocksPort.findUploaded(ownerId, fresh);
    }
}
