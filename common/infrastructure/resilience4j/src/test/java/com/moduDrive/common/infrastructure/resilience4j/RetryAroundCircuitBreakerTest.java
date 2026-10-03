package com.moduDrive.common.infrastructure.resilience4j;

import com.moduDrive.common.core.exception.BusinessException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.hc.client5.http.ConnectTimeoutException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The shared config wraps Feign calls as Retry ( CircuitBreaker ( call ) ), fallback on @Retry (spec 006 1-2). */
@SpringBootTest(
        classes = RetryAroundCircuitBreakerTest.Config.class,
        properties = {
                "spring.config.import=classpath:application-resilience4j.yml",
                "resilience4j.retry.configs.default.wait-duration=1ms"
        })
class RetryAroundCircuitBreakerTest {

    @Autowired
    private FlakyClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("testCircuitBreaker").reset();
        client.resetCalls();
    }

    @Test
    @DisplayName("일시적인 실패는 3번 시도하고, 서킷 브레이커는 시도마다 기록한 뒤 fallback이 한 번 답한다")
    void retriesAndRecordsEveryAttempt() {
        assertThatThrownBy(client::call)
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getExceptionCase()).isEqualTo(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE));

        assertThat(client.calls()).isEqualTo(3);
        assertThat(circuitBreakerRegistry.circuitBreaker("testCircuitBreaker").getMetrics().getNumberOfFailedCalls())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("읽기 시간 초과는 재시도하지 않지만, 서킷 브레이커는 실패로 기록하고 fallback은 시간 초과로 답한다")
    void doesNotRetryAReadTimeout() {
        assertThatThrownBy(client::callTimingOut)
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getExceptionCase()).isEqualTo(CircuitBreakerExceptionCase.CONNECTION_TIMEOUT));

        assertThat(client.calls()).isEqualTo(1);
        assertThat(circuitBreakerRegistry.circuitBreaker("testCircuitBreaker").getMetrics().getNumberOfFailedCalls())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("연결 실패는 3번 시도한다")
    void retriesAConnectFailure() {
        assertThatThrownBy(client::callRefused)
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getExceptionCase()).isEqualTo(CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE));

        assertThat(client.calls()).isEqualTo(3);
    }

    @Test
    @DisplayName("연결 시간 초과는 3번 시도한다")
    void retriesAConnectTimeout() {
        assertThatThrownBy(client::callConnectTimingOut)
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getExceptionCase()).isEqualTo(CircuitBreakerExceptionCase.CONNECTION_TIMEOUT));

        assertThat(client.calls()).isEqualTo(3);
    }

    @Test
    @DisplayName("서킷이 열려 있으면 재시도하지 않고 바로 fallback이 답한다")
    void doesNotRetryAnOpenCircuit() {
        circuitBreakerRegistry.circuitBreaker("testCircuitBreaker").transitionToOpenState();

        assertThatThrownBy(client::call)
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getExceptionCase()).isEqualTo(CircuitBreakerExceptionCase.SERVICE_IS_OPEN));

        assertThat(client.calls()).isZero();
    }

    @Configuration
    @EnableAutoConfiguration
    static class Config {

        @Bean
        FlakyClient flakyClient() {
            return new FlakyClient();
        }
    }

    static class FlakyClient {

        // Read through methods: the bean is a CGLIB proxy, whose own fields are never set.
        private final AtomicInteger calls = new AtomicInteger();

        public int calls() {
            return calls.get();
        }

        public void resetCalls() {
            calls.set(0);
        }

        @CircuitBreaker(name = "testCircuitBreaker")
        @Retry(name = "testRetry", fallbackMethod = "fallback")
        public String call() {
            calls.incrementAndGet();
            Request request = Request.create(Request.HttpMethod.GET, "/test", Map.of(), null, StandardCharsets.UTF_8, null);
            throw new FeignException.ServiceUnavailable("unavailable", request, null, Map.of());
        }

        @CircuitBreaker(name = "testCircuitBreaker")
        @Retry(name = "testRetry", fallbackMethod = "fallback")
        public String callTimingOut() {
            calls.incrementAndGet();
            throw ioFailure(new SocketTimeoutException("Read timed out"));
        }

        @CircuitBreaker(name = "testCircuitBreaker")
        @Retry(name = "testRetry", fallbackMethod = "fallback")
        public String callRefused() {
            calls.incrementAndGet();
            throw ioFailure(new ConnectException("Connection refused"));
        }

        @CircuitBreaker(name = "testCircuitBreaker")
        @Retry(name = "testRetry", fallbackMethod = "fallback")
        public String callConnectTimingOut() {
            calls.incrementAndGet();
            throw ioFailure(new ConnectTimeoutException("Connect timed out"));
        }

        // What Feign throws when the HTTP client fails with an IOException.
        private static RetryableException ioFailure(IOException cause) {
            Request request = Request.create(Request.HttpMethod.GET, "/test", Map.of(), null, StandardCharsets.UTF_8, null);
            return new RetryableException(-1, cause.getMessage(), Request.HttpMethod.GET, cause, (Long) null, request);
        }

        public String fallback(Throwable cause) {
            return FeignFallbackUtils.handleFallback(cause);
        }
    }
}
