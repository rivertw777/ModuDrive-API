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
