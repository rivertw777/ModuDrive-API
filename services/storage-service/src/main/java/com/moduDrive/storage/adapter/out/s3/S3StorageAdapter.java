package com.moduDrive.storage.adapter.out.s3;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@PersistenceAdapter
class S3StorageAdapter implements StoreBlocksPort, RetrieveBlocksPort {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageAdapter.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    // ponytail: hardcoded ceiling, not a config value. 5GB max file / 4MB default block size is
    // ~1250 blocks in practice; 100k leaves generous headroom for smaller client chunk sizes
    // while still keeping `new ArrayList<>(blockCount)` bounded (a caller-supplied blockCount
    // with no cap at all lets one download request pre-allocate an OOM-sized array).
    private static final int MAX_BLOCK_COUNT = 100_000;

    private final S3Client s3Client;
    private final StorageProperties properties;
    private final SecretKeySpec key;

    S3StorageAdapter(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.key = new SecretKeySpec(Base64.getDecoder().decode(properties.getEncryptionKey()), "AES");
    }

    @Override
    public int storeBlocks(String s3BasePath, List<byte[]> rawBlocks) {
        int uploaded = 0;
        try {
            for (int i = 0; i < rawBlocks.size(); i++) {
                String key = s3BasePath + "/block_" + i;
                byte[] processed = encrypt(compress(rawBlocks.get(i)), key);
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(properties.getS3().getBucket())
                                .key(key)
                                .build(),
                        RequestBody.fromBytes(processed)
                );
                uploaded++;
            }
        } catch (RuntimeException e) {
            // Best-effort cleanup of whatever already landed in the bucket — a failed multi-GB
            // upload retried a few times would otherwise leave that many GB of unreferenced
            // blocks behind (#212). Cleanup failing must not hide the original cause.
            deleteBestEffort(s3BasePath, uploaded);
            throw new BusinessException(StorageExceptionCase.STORAGE_ERROR);
        }
        return rawBlocks.size();
    }

    private void deleteBestEffort(String s3BasePath, int uploadedCount) {
        if (uploadedCount == 0) {
            return;
        }
        try {
            List<ObjectIdentifier> ids = IntStream.range(0, uploadedCount)
                    .mapToObj(i -> ObjectIdentifier.builder().key(s3BasePath + "/block_" + i).build())
                    .toList();
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .delete(Delete.builder().objects(ids).build())
                    .build());
        } catch (RuntimeException cleanupFailure) {
            logger.error("Failed to clean up {} orphaned block(s) under {}", uploadedCount, s3BasePath, cleanupFailure);
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
    public List<byte[]> retrieveBlocks(String s3BasePath, int blockCount) {
        if (blockCount > MAX_BLOCK_COUNT) {
            throw new BusinessException(StorageExceptionCase.TOO_MANY_BLOCKS);
        }
        List<byte[]> blocks = new ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            blocks.add(fetchBlock(s3BasePath, i));
        }
        return blocks;
    }

    @Override
    public void streamBlocks(String s3BasePath, int blockCount, OutputStream out) {
        if (blockCount > MAX_BLOCK_COUNT) {
            throw new BusinessException(StorageExceptionCase.TOO_MANY_BLOCKS);
        }
        try {
            for (int i = 0; i < blockCount; i++) {
                out.write(fetchBlock(s3BasePath, i));
            }
        } catch (IOException e) {
            throw new RuntimeException("streaming download failed", e);
        }
    }

    private byte[] fetchBlock(String s3BasePath, int index) {
        String key = s3BasePath + "/block_" + index;
        byte[] encrypted = s3Client.getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(properties.getS3().getBucket())
                        .key(key)
                        .build()
        ).asByteArray();
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
