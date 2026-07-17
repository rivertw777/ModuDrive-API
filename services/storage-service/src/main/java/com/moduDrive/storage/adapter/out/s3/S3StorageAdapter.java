package com.moduDrive.storage.adapter.out.s3;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@PersistenceAdapter
@RequiredArgsConstructor
class S3StorageAdapter implements StoreBlocksPort, RetrieveBlocksPort {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final byte[] DEV_KEY = Arrays.copyOf("modudrive-dev-key".getBytes(), 16);

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public int storeBlocks(String s3BasePath, List<byte[]> rawBlocks) {
        for (int i = 0; i < rawBlocks.size(); i++) {
            byte[] processed = encrypt(compress(rawBlocks.get(i)));
            String key = s3BasePath + "/block_" + i;
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getS3().getBucket())
                            .key(key)
                            .build(),
                    RequestBody.fromBytes(processed)
            );
        }
        return rawBlocks.size();
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

    private byte[] encrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(DEV_KEY, "AES"));
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("encryption failed", e);
        }
    }

    @Override
    public List<byte[]> retrieveBlocks(String s3BasePath, int blockCount) {
        List<byte[]> blocks = new ArrayList<>(blockCount);
        for (int i = 0; i < blockCount; i++) {
            String key = s3BasePath + "/block_" + i;
            byte[] encrypted = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(properties.getS3().getBucket())
                            .key(key)
                            .build()
            ).asByteArray();
            blocks.add(decompress(decrypt(encrypted)));
        }
        return blocks;
    }

    private byte[] decrypt(byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(DEV_KEY, "AES"));
            return cipher.doFinal(data);
        } catch (Exception e) {
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
