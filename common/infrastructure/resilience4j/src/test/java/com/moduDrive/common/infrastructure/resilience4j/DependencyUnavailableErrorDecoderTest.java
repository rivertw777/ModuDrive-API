package com.moduDrive.common.infrastructure.resilience4j;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyUnavailableErrorDecoderTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.POST, "http://storage-service/x",
            Map.of(), new byte[0], StandardCharsets.UTF_8, null);

    private final DependencyUnavailableErrorDecoder decoder = new DependencyUnavailableErrorDecoder();

    @Test
    @DisplayName("X-Dependency-Unavailable이 붙은 503은 Retry-After가 있어도 DependencyUnavailableException으로 바꾼다")
    void decodesAMarked503() {
        // given
        Response response = response(503, Map.of(
                "Retry-After", List.of("10"), "x-dependency-unavailable", List.of("true")));

        // when
        Exception decoded = decoder.decode("StorageClient#findUploadedBlocks", response);

        // then
        assertThat(decoded).isInstanceOf(DependencyUnavailableException.class)
                .isInstanceOf(FeignException.ServiceUnavailable.class);
        assertThat(((FeignException) decoded).contentUTF8()).isEqualTo("{\"message\":\"m\"}");
    }

    @Test
    @DisplayName("표시 없는 503은 Feign 기본대로 바꾼다 — Retry-After가 있으면 RetryableException, 없으면 ServiceUnavailable")
    void leavesAnUnmarked503ToFeign() {
        assertThat(decoder.decode("m", response(503, Map.of("Retry-After", List.of("10")))))
                .isInstanceOf(RetryableException.class);
        assertThat(decoder.decode("m", response(503, Map.of())))
                .isExactlyInstanceOf(FeignException.ServiceUnavailable.class);
    }

    @Test
    @DisplayName("503이 아니면 표시가 있어도 Feign 기본대로 바꾼다")
    void leavesOtherStatusesToFeign() {
        assertThat(decoder.decode("m", response(502, Map.of("X-Dependency-Unavailable", List.of("true")))))
                .isExactlyInstanceOf(FeignException.BadGateway.class);
    }

    private static Response response(int status, Map<String, Collection<String>> headers) {
        return Response.builder().status(status).reason("x").request(REQUEST).headers(headers)
                .body("{\"message\":\"m\"}", StandardCharsets.UTF_8).build();
    }
}
