package com.moduDrive.storage.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

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
            S3Client client = new S3Config().s3Client(properties);

            // then
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("ap-northeast-2");
        }
    }
}
