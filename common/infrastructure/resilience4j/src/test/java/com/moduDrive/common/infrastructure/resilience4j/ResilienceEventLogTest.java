package com.moduDrive.common.infrastructure.resilience4j;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ResilienceEventLogTest {

    @Test
    @DisplayName("시작 뒤에 생긴 서킷 브레이커도 OPEN 전환을 WARN으로 남긴다")
    void logsOpeningOfACircuitCreatedAfterStartup(CapturedOutput output) {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        new CircuitBreakerEventConfig(registry).registerCircuitBreakerEventListeners();

        registry.circuitBreaker("lateCircuitBreaker").transitionToOpenState();

        assertThat(output).containsPattern("WARN.*CircuitBreaker 'lateCircuitBreaker' state changed from CLOSED to OPEN");
    }

    @Test
    @DisplayName("재시도를 다 쓰고 실패하면 WARN으로 남긴다")
    void logsGivingUpAtWarn(CapturedOutput output) {
        RetryRegistry registry = RetryRegistry.of(RetryConfig.custom().waitDuration(Duration.ofMillis(1)).build());
        new RetryEventConfig(registry).registerRetryEventListeners();
        Retry retry = registry.retry("lateRetry");

        assertThatThrownBy(() -> retry.executeRunnable(() -> {
            throw new IllegalStateException("down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(output).containsPattern("WARN.*Retry 'lateRetry' gave up after 3 attempts: java.lang.IllegalStateException: down");
    }
}
