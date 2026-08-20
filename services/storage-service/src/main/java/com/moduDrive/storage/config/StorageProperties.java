package com.moduDrive.storage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private S3Properties s3 = new S3Properties();
    private int blockSize = 4 * 1024 * 1024;
    /** Base64-encoded AES key (16/24/32 raw bytes), e.g. generated via `openssl rand -base64 32`. */
    private String encryptionKey;

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
