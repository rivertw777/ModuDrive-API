package com.moduDrive.storage.adapter.out.s3;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.storage.application.port.out.PresignBlockUploadPort;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.domain.model.Blocks;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Content-Length, x-amz-checksum-sha256 and If-None-Match are signed into the URL, so S3 refuses any
 * other size (403), bytes whose SHA-256 differs (400 BadDigest), and any PUT once the block is there
 * (412) — one URL can't be replayed for an hour to pile up versions (prod keeps 30 days of them). */
@PersistenceAdapter
@RequiredArgsConstructor
class S3BlockUploadPresigner implements PresignBlockUploadPort {

    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    @Override
    public String presignUpload(String key, int size, String hash) {
        return s3Presigner.presignPutObject(presign -> presign
                .signatureDuration(Blocks.UPLOAD_URL_TTL)
                .putObjectRequest(put -> put
                        .bucket(properties.getS3().getBucket())
                        .key(key)
                        .contentLength((long) size)
                        .checksumSHA256(Blocks.sha256Base64(hash))
                        .ifNoneMatch("*")))
                .url().toString();
    }
}
