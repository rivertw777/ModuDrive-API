package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.storage.application.port.in.usecase.FindUploadedBlocksUseCase;
import com.moduDrive.storage.application.port.out.FindUploadedBlocksPort;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class FindUploadedBlocksService implements FindUploadedBlocksUseCase {

    private final FindUploadedBlocksPort findUploadedBlocksPort;

    @Override
    public Map<String, Integer> findUploadedBlocks(UUID ownerId, List<String> hashes) {
        return findUploadedBlocksPort.findUploaded(ownerId, hashes);
    }
}
