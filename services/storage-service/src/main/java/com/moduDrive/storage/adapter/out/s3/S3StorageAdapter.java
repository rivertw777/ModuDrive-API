package com.moduDrive.storage.adapter.out.s3;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import com.moduDrive.storage.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.domain.model.Blocks;
import com.moduDrive.storage.exception.StorageExceptionCase;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@PersistenceAdapter
class S3StorageAdapter implements RetrieveBlocksPort, DeleteBlocksPort, FindUploadedBlocksPort {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageAdapter.class);
    /** S3 calls one commit's lookup has in flight at once (spec 001 2-3) — HEADs, or listing pages —
     * so one commit doesn't take every seat. */
    static final int LOOKUP_PARALLELISM = 8;
    /** From this many hashes the lookup lists the owner's blocks instead of a HEAD each: a listing
     * page answers for up to 1,000 keys. Below it, the 16 key ranges cost more calls than HEADs. */
    static final int LIST_MIN_HASHES = 64;
    /** Hashes asked about per extra listing page, past one page a range, before the lookup gives up
     * on listing — an owner with many blocks in the asked ranges — and HEADs instead.
     * ponytail: fixed ratio (~4 hashes a page), tune from S3 latencies if owners get that large. */
    static final int HASHES_PER_LIST_PAGE = 4;
    // A real version has at most 1,280 blocks (5GB / 4MB); this only stops a malformed one from
    // pre-allocating an oversized list.
    private static final int MAX_BLOCK_COUNT = 100_000;

    private final S3Client s3Client;
    private final StorageProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead lookupBulkhead;
    private final Bulkhead readBulkhead;
    private final Bulkhead deleteBulkhead;

    S3StorageAdapter(S3Client s3Client, StorageProperties properties,
                     CircuitBreakerRegistry circuitBreakerRegistry, BulkheadRegistry bulkheadRegistry) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("s3CircuitBreaker");
        this.lookupBulkhead = bulkheadRegistry.bulkhead("s3Lookup");
        this.readBulkhead = bulkheadRegistry.bulkhead("s3Read");
        this.deleteBulkhead = bulkheadRegistry.bulkhead("s3Delete");
    }

    /** Spec 006 2-4: every S3 call takes a seat in its bulkhead, then goes through the circuit. A
     * full bulkhead or an open circuit answers at once instead of tying up a request thread. */
    private <T> T callS3(Bulkhead bulkhead, Supplier<T> call) {
        try {
            return Bulkhead.decorateSupplier(bulkhead, CircuitBreaker.decorateSupplier(circuitBreaker, call)).get();
        } catch (CallNotPermittedException | BulkheadFullException e) {
            logger.warn("S3 call refused: {}", e.getMessage());
            throw new BusinessException(StorageExceptionCase.STORAGE_UNAVAILABLE);
        }
    }

    /** Spec 001 2-3: a block counts as uploaded only if S3 stored it with a SHA-256 checksum — which
     * the upload URL signs, so S3 refused any PUT whose bytes didn't match the hash. Any lookup failure
     * fails the whole answer: a block reported missing would only be uploaded again, but one reported
     * present must really be there. */
    @Override
    public Map<String, Integer> findUploaded(UUID ownerId, Collection<String> hashes) {
        if (hashes.isEmpty()) {
            return Map.of();
        }
        if (hashes.size() < LIST_MIN_HASHES) {
            return headUploaded(ownerId, hashes);
        }
        Map<Character, TreeSet<String>> byRange = new TreeMap<>();
        hashes.forEach(hash -> byRange.computeIfAbsent(hash.charAt(0), c -> new TreeSet<>()).add(hash));
        List<TreeSet<String>> ranges = List.copyOf(byRange.values());
        // A page for each range, and more as the hashes grow.
        AtomicInteger pagesLeft = new AtomicInteger(ranges.size() + hashes.size() / HASHES_PER_LIST_PAGE);
        List<Map<String, Integer>> listed = inParallel(ownerId, ranges.stream()
                .<Callable<Map<String, Integer>>>map(range -> () -> listRange(ownerId, range, pagesLeft)).toList());
        Map<String, Integer> uploaded = new HashMap<>();
        List<String> unlisted = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            if (listed.get(i) != null) {
                uploaded.putAll(listed.get(i));
            } else {
                unlisted.addAll(ranges.get(i));
            }
        }
        // The ranges the pages ran out on — the owner's blocks are dense there: HEADs are fewer calls.
        uploaded.putAll(headUploaded(ownerId, unlisted));
        return uploaded;
    }

    private Map<String, Integer> headUploaded(UUID ownerId, Collection<String> hashes) {
        if (hashes.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> uploaded = new HashMap<>();
        List<String> asked = List.copyOf(hashes);
        List<Optional<Integer>> sizes = inParallel(ownerId,
                asked.stream().<Callable<Optional<Integer>>>map(hash -> () -> uploadedSize(Blocks.key(ownerId, hash), hash)).toList());
        for (int i = 0; i < asked.size(); i++) {
            int k = i;
            sizes.get(i).ifPresent(size -> uploaded.put(asked.get(k), size));
        }
        return uploaded;
    }

    /** The asked hashes of one first hex digit, by listing the owner's keys from just before the
     * smallest to the largest. Null when the lookup's pages ran out first. */
    private Map<String, Integer> listRange(UUID ownerId, NavigableSet<String> range, AtomicInteger pagesLeft) {
        String ownerPrefix = Blocks.key(ownerId, "");
        String first = Blocks.key(ownerId, range.first());
        String last = Blocks.key(ownerId, range.last());
        Map<String, Integer> uploaded = new HashMap<>();
        String token = null;
        while (true) {
            if (pagesLeft.decrementAndGet() < 0) {
                return null;
            }
            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                    .bucket(properties.getS3().getBucket())
                    .prefix(ownerPrefix + range.first().charAt(0));
            // Exclusive: the first hash's own key, one character short, sorts just before it.
            ListObjectsV2Request request = (token == null
                    ? builder.startAfter(first.substring(0, first.length() - 1))
                    : builder.continuationToken(token)).build();
            ListObjectsV2Response page = callS3(lookupBulkhead, () -> s3Client.listObjectsV2(request));
            for (S3Object object : page.contents()) {
                String hash = object.key().substring(ownerPrefix.length());
                if (range.contains(hash) && object.checksumAlgorithm().contains(ChecksumAlgorithm.SHA256)) {
                    uploaded.put(hash, Math.toIntExact(object.size()));
                }
            }
            if (!Boolean.TRUE.equals(page.isTruncated()) || page.contents().isEmpty()
                    || page.contents().getLast().key().compareTo(last) >= 0) {
                return uploaded;
            }
            token = page.nextContinuationToken();
        }
    }

    /** The lookups' results in order, {@link #LOOKUP_PARALLELISM} at a time. */
    private <T> List<T> inParallel(UUID ownerId, List<Callable<T>> lookups) {
        Semaphore inFlight = new Semaphore(LOOKUP_PARALLELISM);
        // Virtual threads wait on S3, not a platform thread each; the context carries the trace over.
        ExecutorService executor = Context.taskWrapping(Executors.newVirtualThreadPerTaskExecutor());
        try {
            List<Future<T>> futures = new ArrayList<>(lookups.size());
            for (Callable<T> lookup : lookups) {
                futures.add(executor.submit(() -> {
                    inFlight.acquire();
                    try {
                        return lookup.call();
                    } finally {
                        inFlight.release();
                    }
                }));
            }
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(StorageExceptionCase.STORAGE_ERROR);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof BusinessException business) {
                throw business;
            }
            logger.error("Failed to look up uploaded blocks of owner {}", ownerId, e.getCause());
            throw new BusinessException(StorageExceptionCase.STORAGE_ERROR);
        } finally {
            // Once the answer is a failure, the calls still queued would only spend S3 seats on it.
            executor.shutdownNow();
        }
    }

    private Optional<Integer> uploadedSize(String key, String hash) {
        HeadObjectResponse head;
        try {
            head = callS3(lookupBulkhead, () -> s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(key)
                    .checksumMode(ChecksumMode.ENABLED)
                    .build()));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
        return Blocks.sha256Base64(hash).equals(head.checksumSHA256())
                ? Optional.of(Math.toIntExact(head.contentLength()))
                : Optional.empty();
    }

    /** HEAD, then a delete conditional on the ETag that HEAD saw: an object written at the key after
     * {@code decidedAt}, or between the HEAD and the delete, is kept. The same bytes can't be written
     * over an existing block anyway — upload URLs carry If-None-Match — so a block re-uploaded while
     * still here is protected by the caller's schedule check, not by this. A failure propagates, so
     * the caller can retry. */
    @Override
    public void deleteUnlessRewritten(String key, Instant decidedAt) {
        String bucket = properties.getS3().getBucket();
        HeadObjectResponse head;
        try {
            head = callS3(deleteBulkhead, () -> s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()));
        } catch (BusinessException e) {
            throw e;
        } catch (S3Exception e) {
            // NoSuchKeyException, or a bare 404 — HEAD has no body to carry an error code.
            if (e.statusCode() == 404) {
                return;
            }
            logger.error("Failed to look up block {} before deleting it", key, e);
            throw new BusinessException(StorageExceptionCase.STORAGE_ERROR);
        } catch (RuntimeException e) {
            logger.error("Failed to look up block {} before deleting it", key, e);
            throw new BusinessException(StorageExceptionCase.STORAGE_ERROR);
        }
        // S3 keeps LastModified to the second, so compare at that precision — a write in the same
        // second as the decision counts as after it. Keeping a block too long only costs storage.
        if (!head.lastModified().isBefore(decidedAt.truncatedTo(ChronoUnit.SECONDS))) {
            return;
        }
        try {
            callS3(deleteBulkhead, () -> s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .ifMatch(head.eTag())
                    .build()));
        } catch (BusinessException e) {
            throw e;
        } catch (S3Exception e) {
            if (e.statusCode() == 412) {
                return; // rewritten after the HEAD — the new upload keeps it
            }
            logger.error("Failed to delete block {}", key, e);
            throw new BusinessException(StorageExceptionCase.STORAGE_ERROR);
        }
    }

    @Override
    public List<byte[]> retrieveBlocks(List<String> keys) {
        requireWithinBlockCountLimit(keys);
        List<byte[]> blocks = new ArrayList<>(keys.size());
        for (String key : keys) {
            blocks.add(fetchBlock(key));
        }
        return blocks;
    }

    @Override
    public void streamBlocks(List<String> keys, OutputStream out) {
        requireWithinBlockCountLimit(keys);
        try {
            for (String key : keys) {
                out.write(fetchBlock(key));
            }
        } catch (IOException e) {
            throw new RuntimeException("streaming download failed", e);
        }
    }

    private static void requireWithinBlockCountLimit(List<String> keys) {
        if (keys.size() > MAX_BLOCK_COUNT) {
            throw new BusinessException(StorageExceptionCase.TOO_MANY_BLOCKS);
        }
    }

    /** The read seat is held only while this one block comes back from S3 — not while a slow
     * client takes the bytes. */
    private byte[] fetchBlock(String key) {
        return callS3(readBulkhead, () -> s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(properties.getS3().getBucket())
                        .key(key)
                        .build()
        ).asByteArray());
    }
}
