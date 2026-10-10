package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.file.application.port.out.ClaimUnreferencedBlocksPort;
import com.moduDrive.file.application.port.out.FindCommittedBlocksPort;
import com.moduDrive.file.application.port.out.LockCommittedBlocksPort;
import com.moduDrive.file.application.port.out.ReferenceBlocksPort;
import com.moduDrive.file.application.port.out.ReleaseBlocksPort;
import com.moduDrive.file.domain.model.FileVersion;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** The block aggregate — one row per committed block of an owner, with how many version entries
 * point at it (spec 001). */
@PersistenceAdapter
@RequiredArgsConstructor
class BlockPersistenceAdapter implements LockCommittedBlocksPort, FindCommittedBlocksPort, ReferenceBlocksPort,
        ReleaseBlocksPort, ClaimUnreferencedBlocksPort {

    private static final Logger logger = LoggerFactory.getLogger(BlockPersistenceAdapter.class);

    private final SpringDataBlockRepository blockRepository;

    @Override
    public Map<String, Integer> lockCommittedBlocks(UUID ownerId, Collection<String> hashes) {
        if (hashes.isEmpty()) {
            return Map.of();
        }
        return blockRepository.lockByOwnerIdAndHashes(ownerId, hashes).stream()
                .collect(Collectors.toMap(b -> b.getId().getHash(), BlockJpaEntity::getSize));
    }

    @Override
    public Set<String> findCommittedHashes(UUID ownerId, Collection<String> hashes) {
        if (hashes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(blockRepository.findHashesByOwnerIdAndHashes(ownerId, hashes));
    }

    @Override
    public void referenceBlocks(UUID ownerId, Map<String, Integer> sizeByHash, Map<String, Integer> countByHash) {
        // Hash order, same as the lock — keeps concurrent commits from deadlocking on new rows.
        new TreeMap<>(countByHash).forEach((hash, count) ->
                blockRepository.reference(ownerId, hash, sizeByHash.get(hash), count));
    }

    @Override
    public void releaseBlocks(List<FileVersion> versions) {
        LocalDateTime now = LocalDateTime.now();
        // All versions summed and in hash order, the same order a commit locks and references in —
        // per version, two versions sharing blocks would release them out of order and could
        // deadlock against a commit.
        Map<UUID, Map<String, Integer>> countsByOwner = new TreeMap<>();
        for (FileVersion version : versions) {
            Map<String, Integer> counts = countsByOwner.computeIfAbsent(version.getOwnerId(), owner -> new TreeMap<>());
            version.getHashes().forEach(hash -> counts.merge(hash, 1, Integer::sum));
        }
        countsByOwner.forEach((ownerId, counts) -> counts.forEach((hash, count) -> {
            if (blockRepository.release(ownerId, hash, count, now) == 0) {
                // A version pointing at a block with no row: references were lost somewhere.
                logger.error("Released block {} of owner {} has no row", hash, ownerId);
            }
        }));
    }

    @Override
    public List<UnreferencedBlock> claimUnreferenced(LocalDateTime cutoff, int limit) {
        List<BlockJpaEntity> rows = blockRepository.lockUnreferencedBefore(cutoff, limit);
        blockRepository.deleteAllInBatch(rows);
        return rows.stream()
                .map(row -> new UnreferencedBlock(row.getId().getOwnerId(), row.getId().getHash()))
                .toList();
    }

}
