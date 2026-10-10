package com.moduDrive.storage.adapter.out.s3;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.exception.StorageExceptionCase;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@PersistenceAdapter
class S3StorageAdapter implements StoreBlocksPort, RetrieveBlocksPort, DeleteBlocksPort {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageAdapter.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    // ponytail: hardcoded ceiling, not a config value. 5GB max file / 4MB blocks is 1,280 blocks;
    // this only guards against a malformed version pre-allocating an oversized list.
    private static final int MAX_BLOCK_COUNT = 100_000;

    private final S3Client s3Client;
    private final StorageProperties properties;
    private final SecretKeySpec key;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead writeBulkhead;
    private final Bulkhead readBulkhead;
    private final Bulkhead deleteBulkhead;

    S3StorageAdapter(S3Client s3Client, StorageProperties properties,
                     CircuitBreakerRegistry circuitBreakerRegistry, BulkheadRegistry bulkheadRegistry) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.getEncryptionKey()), "AES");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("s3CircuitBreaker");
        this.writeBulkhead = bulkheadRegistry.bulkhead("s3Write");
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

    /** No cleanup on failure: the client retries the same block, and a block that is never
     * committed is deleted by the uncommitted-upload sweep. */
    @Override
    public void storeBlock(String key, byte[] rawBlock) {
        byte[] sealed = encrypt(compress(rawBlock), key);
        try {
            callS3(writeBulkhead, () -> s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getS3().getBucket())
                            .key(key)
                            .build(),
                    RequestBody.fromBytes(sealed)
            ));
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            logger.error("Failed to store block {}", key, e);
            throw new BusinessException(StorageExceptionCase.STORAGE_ERROR);
        }
    }

    /** HEAD, then a delete conditional on the ETag that HEAD saw: an upload of the same key landing
     * between the two changes the ETag (the IV is random, so even the same bytes encrypt
     * differently) and the delete is refused instead of taking the new upload with it. A failure
     * propagates, so the caller can retry. */
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

    private byte[] compress(byte[] data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        } catch (IOException e) {
            throw new RuntimeException("compression failed", e);
        }
        return bos.toByteArray();
    }

    private byte[] encrypt(byte[] data, String objectKey) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            // Binds the ciphertext to the S3 key it's stored under — without this, a block
            // copied from one object's location to another's would still decrypt cleanly, since
            // GCM's tag authenticates only the plaintext (#216).
            cipher.updateAAD(objectKey.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(data);
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("encryption failed", e);
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
        byte[] encrypted = callS3(readBulkhead, () -> s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(properties.getS3().getBucket())
                        .key(key)
                        .build()
        ).asByteArray());
        return decompress(decrypt(encrypted, key));
    }

    private byte[] decrypt(byte[] data, String objectKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, data, 0, GCM_IV_LENGTH));
            cipher.updateAAD(objectKey.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(data, GCM_IV_LENGTH, data.length - GCM_IV_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("decryption failed", e);
        }
    }

    private byte[] decompress(byte[] data) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data));
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gzip.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("decompression failed", e);
        }
    }
}
