package com.moduDrive.common.infrastructure.resilience4j;

import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceUnavailableResponseTest {

    private static final Request REQUEST = Request.create(Request.HttpMethod.POST, "http://storage-service/x",
            Map.of(), new byte[0], StandardCharsets.UTF_8, null);

    private final ServiceUnavailableResponse predicate = new ServiceUnavailableResponse();

    @Test
    @DisplayName("Retry-After가 붙은 503(우리 서비스가 답한 503)은 Feign이 RetryableException으로 바꿔도 본다")
    void matchesA503WithRetryAfter() {
        // given
        Exception decoded = decode(503, Map.of("Retry-After", List.of("10")));

        // when
        boolean matched = predicate.test(decoded);

        // then
        assertThat(decoded).isInstanceOf(RetryableException.class);
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("Retry-After 없는 503(중간 프록시가 만든 응답)은 보지 않는다")
    void doesNotMatchA503WithoutRetryAfter() {
        assertThat(predicate.test(decode(503, Map.of()))).isFalse();
    }

    @Test
    @DisplayName("다른 5xx와 연결 실패는 503으로 보지 않는다")
    void doesNotMatchOtherFailures() {
        RetryableException refused = new RetryableException(-1, "refused", Request.HttpMethod.POST,
                new ConnectException("refused"), (Long) null, REQUEST);

        assertThat(predicate.test(decode(502, Map.of()))).isFalse();
        assertThat(predicate.test(decode(504, Map.of()))).isFalse();
        assertThat(predicate.test(refused)).isFalse();
    }

    private static Exception decode(int status, Map<String, Collection<String>> headers) {
        Response response = Response.builder().status(status).reason("x").request(REQUEST).headers(headers)
                .body("{}", StandardCharsets.UTF_8).build();
        return new ErrorDecoder.Default().decode("StorageClient#findUploadedBlocks", response);
    }
}
