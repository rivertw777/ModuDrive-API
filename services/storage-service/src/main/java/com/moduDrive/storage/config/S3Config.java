package com.moduDrive.storage.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3Config {

    /** An endpoint means an S3 emulator (local LocalStack); without one this talks to real S3.
     * Without keys the SDK's default credential chain applies — the ECS task role on AWS.
     * Every call is a span (bucket, operation, status) under the request that made it. */
    @Bean
    public S3Client s3Client(StorageProperties properties, OpenTelemetry openTelemetry) {
        StorageProperties.S3Properties s3 = properties.getS3();
        boolean customEndpoint = StringUtils.hasText(s3.getEndpoint());

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3.getRegion()))
                .overrideConfiguration(c -> c
                        .addExecutionInterceptor(AwsSdkTelemetry.create(openTelemetry).createExecutionInterceptor())
                        // Spec 006 2-4: bounded time, one retry — the WEB and SQS retry on top.
                        .apiCallAttemptTimeout(s3.getApiCallAttemptTimeout())
                        .apiCallTimeout(s3.getApiCallTimeout())
                        .retryStrategy(AwsRetryStrategy.standardRetryStrategy().toBuilder().maxAttempts(2).build()));
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

    /** Signs block upload URLs (spec 001 6) with the same credentials as {@link #s3Client} — the
     * task role on AWS, so the URL carries its session token. Signing is local: nothing is sent. */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(StorageProperties properties) {
        StorageProperties.S3Properties s3 = properties.getS3();
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(s3.getRegion()));
        if (StringUtils.hasText(s3.getPublicEndpoint())) {
            builder.endpointOverride(URI.create(s3.getPublicEndpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        if (StringUtils.hasText(s3.getAccessKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())));
        }
        return builder.build();
    }

    private void createBucketIfMissing(S3Client client, String bucket, String region) {
        try {
            client.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            // S3 wants the region spelled out for every region but us-east-1, its default.
            try {
                client.createBucket(b -> {
                    b.bucket(bucket);
                    if (!Region.US_EAST_1.id().equals(region)) {
                        b.createBucketConfiguration(c -> c.locationConstraint(region));
                    }
                });
            } catch (BucketAlreadyOwnedByYouException ignored) {
                // LocalStack's init script (init-aws.sh) created it between our head and create.
            }
        }
    }
}
