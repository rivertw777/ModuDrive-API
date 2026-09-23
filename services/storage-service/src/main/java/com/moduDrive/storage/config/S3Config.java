package com.moduDrive.storage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3Config {

    /** An endpoint means an S3 emulator (local LocalStack); without one this talks to real S3.
     * Without keys the SDK's default credential chain applies — the ECS task role on AWS. */
    @Bean
    public S3Client s3Client(StorageProperties properties) {
        StorageProperties.S3Properties s3 = properties.getS3();
        boolean customEndpoint = StringUtils.hasText(s3.getEndpoint());

        S3ClientBuilder builder = S3Client.builder().region(Region.of(s3.getRegion()));
        if (customEndpoint) {
            builder.endpointOverride(URI.create(s3.getEndpoint())).forcePathStyle(true);
        }
        if (StringUtils.hasText(s3.getAccessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())));
        }
        S3Client client = builder.build();

        // Real buckets are provisioned by Terraform and the task role has no CreateBucket permission.
        if (customEndpoint) {
            createBucketIfMissing(client, s3.getBucket(), s3.getRegion());
        }
        return client;
    }

    private void createBucketIfMissing(S3Client client, String bucket, String region) {
        try {
            client.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            // S3 wants the region spelled out for every region but us-east-1, its default.
            client.createBucket(b -> {
                b.bucket(bucket);
                if (!Region.US_EAST_1.id().equals(region)) {
                    b.createBucketConfiguration(c -> c.locationConstraint(region));
                }
            });
        }
    }
}
