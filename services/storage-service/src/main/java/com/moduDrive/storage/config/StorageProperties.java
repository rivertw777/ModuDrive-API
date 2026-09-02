package com.moduDrive.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private S3Properties s3 = new S3Properties();
    private int blockSize = 4 * 1024 * 1024;
    /** Base64-encoded AES key (16/24/32 raw bytes), e.g. generated via `openssl rand -base64 32`. */
    private String encryptionKey;
    /** Per-file download volume allowed within {@link #downloadQuotaWindow} before further
     * downloads of that file are blocked. Default 10 GiB. */
    private long downloadQuotaPerFileBytes = 10L * 1024 * 1024 * 1024;
    private Duration downloadQuotaWindow = Duration.ofHours(24);

    @Getter
    @Setter
    public static class S3Properties {
        private String endpoint;
        private String bucket = "modudrive";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String region = "us-east-1";
    }
}
