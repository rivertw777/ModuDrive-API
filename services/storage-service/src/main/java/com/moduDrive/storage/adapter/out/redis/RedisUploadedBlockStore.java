package com.moduDrive.storage.adapter.out.redis;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.storage.application.port.out.ClaimStaleUploadsPort;
import com.moduDrive.storage.application.port.out.FindScheduledUploadsPort;
import com.moduDrive.storage.application.port.out.RecordUploadedBlockPort;
import com.moduDrive.storage.domain.model.Blocks;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The uncommitted-upload sweep's schedule (spec 001 2-4-1): {@code uploaded-blocks:{<c>}} is a sorted set
 * of {@code ownerId:hash} by the time its upload URL was signed. It is split 16 ways by the hash's first
 * hex digit — one key would put every upload's write on a single shard however many there are. The
 * score is also what a commit's freshness check reads; whether a block actually landed is S3's to say
 * ({@code S3StorageAdapter#findUploaded}), so nothing here records it.
 *
 * <p>Every command and script here touches a single key — so it runs unchanged on MemoryDB (prod,
 * cluster mode), where a multi-key command across slots fails with CROSSSLOT.
 */
@Component
@RequiredArgsConstructor
class RedisUploadedBlockStore implements RecordUploadedBlockPort, ClaimStaleUploadsPort, FindScheduledUploadsPort {

    private static final String INDEX_KEY_PREFIX = "uploaded-blocks:";
    private static final List<String> INDEX_KEYS = "0123456789abcdef".chars()
            .mapToObj(c -> INDEX_KEY_PREFIX + "{" + (char) c + "}").toList();
    private static final String COUNT_KEY_PREFIX = "upload-count:";

    private static final RedisScript<Long> RELEASE_SCRIPT =
            RedisRepository.loadScript("scripts/uploaded-block-release.lua", Long.class);
    private static final RedisScript<Long> COUNT_SCRIPT =
            RedisRepository.loadScript("scripts/upload-count.lua", Long.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CLAIM_STALE_SCRIPT =
            RedisRepository.loadScript("scripts/uploaded-block-claim-stale.lua", List.class);

    private final RedisRepository redisRepository;

    @Override
    public void scheduleSweep(UUID ownerId, String hash) {
        redisRepository.addToSortedSet(indexKey(hash), ownerId + ":" + hash, Instant.now().toEpochMilli());
    }

    @Override
    public boolean tryCountUpload(UUID ownerId, int limit) {
        Long counted = redisRepository.executeScript(COUNT_SCRIPT, List.of(COUNT_KEY_PREFIX + ownerId),
                String.valueOf(limit), String.valueOf(Blocks.UPLOAD_TTL.toMillis()));
        return counted != null && counted == 1L;
    }

    @Override
    public Set<String> findScheduledSince(UUID ownerId, Collection<String> hashes, Instant since) {
        Set<String> scheduled = new HashSet<>();
        // One ZMSCORE per index key: a command can only touch keys in one slot.
        hashes.stream().distinct().collect(Collectors.groupingBy(RedisUploadedBlockStore::indexKey)).forEach((indexKey, group) -> {
            List<Double> scores = redisRepository.scoresInSortedSet(indexKey,
                    group.stream().map(hash -> ownerId + ":" + hash).toList());
            for (int i = 0; i < group.size(); i++) {
                if (scores.get(i) != null && scores.get(i) > since.toEpochMilli()) {
                    scheduled.add(group.get(i));
                }
            }
        });
        return scheduled;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<UploadedBlock> claimStale(Instant cutoff, int limit) {
        // Fewer than limit only once every key is drained, so the sweep's "a short batch is the
        // last" still holds.
        List<UploadedBlock> claimed = new ArrayList<>();
        for (String indexKey : INDEX_KEYS) {
            if (claimed.size() == limit) {
                break;
            }
            List<String> members = redisRepository.executeScript(CLAIM_STALE_SCRIPT, List.of(indexKey),
                    String.valueOf(cutoff.toEpochMilli()), String.valueOf(limit - claimed.size()));
            if (members != null) {
                members.forEach(member -> {
                    String[] parts = member.split(":");
                    claimed.add(new UploadedBlock(UUID.fromString(parts[0]), parts[1]));
                });
            }
        }
        return claimed;
    }

    @Override
    public void release(List<UploadedBlock> blocks, Instant uploadedAt) {
        if (blocks.isEmpty()) {
            return;
        }
        // One script per index key: a script can only touch keys in one slot.
        blocks.stream().collect(Collectors.groupingBy(block -> indexKey(block.hash()))).forEach((indexKey, group) -> {
            List<String> args = new ArrayList<>(group.size() + 1);
            args.add(String.valueOf(uploadedAt.toEpochMilli()));
            group.forEach(block -> args.add(block.ownerId() + ":" + block.hash()));
            redisRepository.executeScript(RELEASE_SCRIPT, List.of(indexKey), args.toArray(String[]::new));
        });
    }

    /** Hashes are hex SHA-256, so the first digit spreads them evenly over the 16 keys. */
    static String indexKey(String hash) {
        return INDEX_KEY_PREFIX + "{" + hash.charAt(0) + "}";
    }
}
