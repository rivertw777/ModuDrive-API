package com.moduDrive.file.application.service;

import com.moduDrive.common.core.annotation.UseCase;
import com.moduDrive.file.application.port.in.usecase.FindCommittedBlocksUseCase;
import com.moduDrive.file.application.port.out.FindCommittedBlocksPort;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@UseCase
@RequiredArgsConstructor
class FindCommittedBlocksService implements FindCommittedBlocksUseCase {

    private final FindCommittedBlocksPort findCommittedBlocksPort;

    @Transactional(readOnly = true)
    @Override
    public Set<String> findCommittedBlocks(UUID ownerId, List<String> hashes) {
        return findCommittedBlocksPort.findCommittedHashes(ownerId, hashes);
    }
}
