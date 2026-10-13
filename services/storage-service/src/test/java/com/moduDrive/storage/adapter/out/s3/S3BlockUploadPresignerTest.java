package com.moduDrive.storage.adapter.out.s3;

import com.moduDrive.storage.config.S3Config;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.domain.model.Blocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class S3BlockUploadPresignerTest {

    private static final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private static StorageProperties properties(String publicEndpoint) {
        StorageProperties properties = new StorageProperties();
        properties.getS3().setBucket("modudrive");
        properties.getS3().setRegion("ap-northeast-2");
        properties.getS3().setAccessKey("test");
        properties.getS3().setSecretKey("test");
        properties.getS3().setPublicEndpoint(publicEndpoint);
        return properties;
    }

    private static String presign(StorageProperties properties) {
        try (S3Presigner presigner = new S3Config().s3Presigner(properties)) {
            return URLDecoder.decode(new S3BlockUploadPresigner(presigner, properties)
                    .presignUpload("blocks/owner/" + HASH, 3, HASH), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("크기·체크섬·If-None-Match를 서명해, S3가 그 바이트를 한 번만 받게 한다")
    void signsTheSizeTheChecksumAndCreateOnly() {
        String url = presign(properties(""));

        assertThat(url).contains("X-Amz-SignedHeaders=content-length;host;if-none-match;x-amz-checksum-sha256");
        assertThat(url).contains("X-Amz-Expires=" + Blocks.UPLOAD_URL_TTL.toSeconds());
    }

    @Test
    @DisplayName("공개 endpoint가 없으면 실제 S3 주소로 서명한다")
    void pointsAtRealS3ByDefault() {
        assertThat(presign(properties(""))).startsWith("https://modudrive.s3.ap-northeast-2.amazonaws.com/blocks/owner/" + HASH + "?");
    }

    @Test
    @DisplayName("공개 endpoint가 있으면 브라우저가 닿는 그 주소로 서명한다 (로컬 LocalStack)")
    void pointsAtThePublicEndpointWhenSet() {
        assertThat(presign(properties("http://localhost:4566"))).startsWith("http://localhost:4566/modudrive/blocks/owner/" + HASH + "?");
    }
}
