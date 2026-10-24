package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.file.application.port.out.ClaimUnreferencedBlocksPort;
import com.moduDrive.file.application.port.out.FindCommittedBlocksPort;
import com.moduDrive.file.application.port.out.LockCommittedBlocksPort;
import com.moduDrive.file.application.port.out.ReferenceBlocksPort;
import com.moduDrive.file.application.port.out.ReleaseBlocksPort;
import com.moduDrive.file.domain.model.FileVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@PersistenceAdapter
@RequiredArgsConstructor
class BlockPersistenceAdapter implements LockCommittedBlocksPort, FindCommittedBlocksPort, ReferenceBlocksPort,
        ReleaseBlocksPort, ClaimUnreferencedBlocksPort {


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
        if (countByHash.isEmpty()) {
            return;
        }
        String[] hashes = countByHash.keySet().toArray(String[]::new);
        Integer[] sizes = new Integer[hashes.length];
        Integer[] counts = new Integer[hashes.length];
        for (int i = 0; i < hashes.length; i++) {
            sizes[i] = sizeByHash.get(hashes[i]);
            counts[i] = countByHash.get(hashes[i]);
        }
        blockRepository.referenceAll(ownerId, hashes, sizes, counts);
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
        countsByOwner.forEach((ownerId, counts) -> {
            long missing = blockRepository.releaseAll(ownerId, counts.keySet().toArray(String[]::new),
                    counts.values().toArray(Integer[]::new), now);
            if (missing > 0) {
                // Versions pointing at blocks with no row: references were lost somewhere.
                log.error("{} released block(s) of owner {} have no row", missing, ownerId);
            }
        });
    }

    @Override
    public void releaseBlocksOfFiles(Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return;
        }
        long missing = blockRepository.releaseAllOf(fileIds.toArray(UUID[]::new), LocalDateTime.now());
        if (missing > 0) {
            // Versions pointing at blocks with no row: references were lost somewhere.
            log.error("{} released block(s) of {} purged file(s) have no row", missing, fileIds.size());
        }
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
