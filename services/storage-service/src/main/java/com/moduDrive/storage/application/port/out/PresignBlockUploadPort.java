package com.moduDrive.storage.application.port.out;

public interface PresignBlockUploadPort {

    /** A PUT URL for {@code key}, valid for {@code Blocks.UPLOAD_URL_TTL}, that S3 accepts only with
     * exactly {@code size} bytes whose SHA-256 is {@code hash}. Signed locally — no call to S3. */
    String presignUpload(String key, int size, String hash);
}
