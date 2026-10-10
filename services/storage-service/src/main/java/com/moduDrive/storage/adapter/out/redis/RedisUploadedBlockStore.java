package com.moduDrive.storage.adapter.out.redis;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.storage.application.port.out.ClaimStaleUploadsPort;
import com.moduDrive.storage.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.storage.application.port.out.RecordUploadedBlockPort;
import com.moduDrive.storage.domain.model.Blocks;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Uploaded blocks waiting to be committed (spec 001). {@code uploaded-block:{ownerId}:{hash}} holds
 * the raw size and expires after {@link Blocks#UPLOAD_TTL}; {@code uploaded-blocks} is a sorted set
 * of {@code ownerId:hash} by upload time, so the sweep can still find a block whose record has
 * already expired.
 */
@Component
@RequiredArgsConstructor
class RedisUploadedBlockStore implements RecordUploadedBlockPort, FindUploadedBlocksPort, ClaimStaleUploadsPort {

    private static final String KEY_PREFIX = "uploaded-block:";
    private static final String INDEX_KEY = "uploaded-blocks";
    private static final String COUNT_KEY_PREFIX = "upload-count:";

    private static final RedisScript<Long> RECORD_SCRIPT =
            RedisRepository.loadScript("scripts/uploaded-block-record.lua", Long.class);
    private static final RedisScript<Long> RELEASE_SCRIPT =
            RedisRepository.loadScript("scripts/uploaded-block-release.lua", Long.class);
    private static final RedisScript<Long> COUNT_SCRIPT =
            RedisRepository.loadScript("scripts/upload-count.lua", Long.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CLAIM_STALE_SCRIPT =
            RedisRepository.loadScript("scripts/uploaded-block-claim-stale.lua", List.class);

    private final RedisRepository redisRepository;

    @Override
    public void recordUploaded(UUID ownerId, String hash, int size) {
        redisRepository.executeScript(RECORD_SCRIPT, List.of(key(ownerId, hash), INDEX_KEY),
                String.valueOf(size),
                String.valueOf(Blocks.UPLOAD_TTL.toMillis()),
                String.valueOf(Instant.now().toEpochMilli()),
                ownerId + ":" + hash);
    }

    @Override
    public void scheduleSweep(UUID ownerId, String hash) {
        redisRepository.addToSortedSet(INDEX_KEY, ownerId + ":" + hash, Instant.now().toEpochMilli());
    }

    @Override
    public boolean tryCountUpload(UUID ownerId, int limit) {
        Long counted = redisRepository.executeScript(COUNT_SCRIPT, List.of(COUNT_KEY_PREFIX + ownerId),
                String.valueOf(limit), String.valueOf(Blocks.UPLOAD_TTL.toMillis()));
        return counted != null && counted == 1L;
    }

    @Override
    public Map<String, Integer> findUploaded(UUID ownerId, Collection<String> hashes) {
        List<String> ordered = List.copyOf(hashes);
        if (ordered.isEmpty()) {
            return Map.of();
        }
        List<String> sizes = redisRepository.multiGet(ordered.stream().map(hash -> key(ownerId, hash)).toList());
        Map<String, Integer> uploaded = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            if (sizes.get(i) != null) {
                uploaded.put(ordered.get(i), Integer.parseInt(sizes.get(i)));
            }
        }
        return uploaded;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UploadedBlock> claimStale(Instant cutoff, int limit) {
        List<String> members = redisRepository.executeScript(CLAIM_STALE_SCRIPT, List.of(INDEX_KEY),
                String.valueOf(cutoff.toEpochMilli()), String.valueOf(limit));
        if (members == null) {
            return List.of();
        }
        return members.stream().map(member -> {
            String[] parts = member.split(":");
            return new UploadedBlock(UUID.fromString(parts[0]), parts[1]);
        }).toList();
    }

    @Override
    public void release(List<UploadedBlock> blocks, Instant uploadedAt) {
        if (blocks.isEmpty()) {
            return;
        }
        List<String> args = new ArrayList<>(blocks.size() + 1);
        args.add(String.valueOf(uploadedAt.toEpochMilli()));
        blocks.forEach(block -> args.add(block.ownerId() + ":" + block.hash()));
        redisRepository.executeScript(RELEASE_SCRIPT, List.of(INDEX_KEY), args.toArray(String[]::new));
    }

    private static String key(UUID ownerId, String hash) {
        return KEY_PREFIX + ownerId + ":" + hash;
    }
}
