package com.moduDrive.storage.adapter.out.s3;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.assertj.core.api.Assertions.assertThat;

class S3UnavailableTest {

    private final S3Unavailable unavailable = new S3Unavailable();

    @Test
    @DisplayName("응답 없음·5xx만 S3 장애로 센다")
    void countsOnlyS3NotAnswering() {
        assertThat(unavailable.test(SdkClientException.create("timeout"))).isTrue();
        assertThat(unavailable.test(S3Exception.builder().statusCode(503).build())).isTrue();
        assertThat(unavailable.test(S3Exception.builder().statusCode(404).build())).isFalse();
    }

    @Test
    @DisplayName("실패한 조회가 스스로 끊은 HEAD는 세지 않는다 — 실패 하나가 서킷을 열지 않게")
    void doesNotCountACallWeAborted() {
        assertThat(unavailable.test(AbortedException.create("interrupted"))).isFalse();
    }
}
