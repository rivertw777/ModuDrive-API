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
    /** Per-file download volume allowed within {@link #downloadQuotaWindow} before further
     * downloads of that file are blocked. Default 10 GiB. */
    private long downloadQuotaPerFileBytes = 10L * 1024 * 1024 * 1024;
    private Duration downloadQuotaWindow = Duration.ofHours(24);
    /** Blocks a download has on their way from S3 at once (spec 002 2). Each holds an {@code s3Read}
     * seat and up to a block of memory. */
    private int downloadReadAhead = 4;

    @Getter
    @Setter
    public static class S3Properties {
        private String endpoint;
        /** Where the browser reaches the same S3 — what presigned upload URLs point at (spec 001 2-4).
         * Locally LocalStack as seen from the host; blank = real AWS S3, the same for both. */
        private String publicEndpoint;
        private String bucket = "modudrive";
        private String accessKey;
        private String secretKey;
        private String region = "ap-northeast-2";
        /** One attempt, and the whole call with its retry (spec 006 2-4-1). The SDK has neither by
         * default, so a hung S3 would hold a request thread forever. */
        private Duration apiCallAttemptTimeout = Duration.ofSeconds(5);
        private Duration apiCallTimeout = Duration.ofSeconds(15);
    }
}
