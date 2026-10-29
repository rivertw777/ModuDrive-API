package com.moduDrive.storage.config;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class S3ConfigTest {

    @Nested
    @DisplayName("endpoint와 키 없이 설정됐을 때 (AWS)")
    class WhenEndpointAndKeysAreBlank {

        @Test
        void buildsRealS3ClientWithoutTouchingBuckets() {
            // given
            StorageProperties properties = new StorageProperties();
            properties.getS3().setEndpoint("");
            properties.getS3().setAccessKey("");
            properties.getS3().setRegion("ap-northeast-2");

            // when — would throw if it tried headBucket/createBucket against a real endpoint
            S3Client client = new S3Config().s3Client(properties, OpenTelemetry.noop(), BulkheadRegistry.ofDefaults());

            // then
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("ap-northeast-2");
        }

        @Test
        void keepsTimeoutsRetriesAndTracingTogether() {
            // given
            StorageProperties properties = new StorageProperties();
            properties.getS3().setEndpoint("");
            properties.getS3().setAccessKey("");
            properties.getS3().setRegion("ap-northeast-2");

            // when
            S3Client client = new S3Config().s3Client(properties, OpenTelemetry.noop(), BulkheadRegistry.ofDefaults());

            // then
            ClientOverrideConfiguration override = client.serviceClientConfiguration().overrideConfiguration();
            assertThat(override.apiCallAttemptTimeout()).contains(Duration.ofSeconds(5));
            assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(15));
            assertThat(override.retryStrategy()).hasValueSatisfying(retry -> assertThat(retry.maxAttempts()).isEqualTo(2));
            assertThat(override.executionInterceptors())
                    .extracting(interceptor -> interceptor.getClass().getName())
                    .anyMatch(name -> name.startsWith("io.opentelemetry.instrumentation.awssdk"));
        }
    }
}
