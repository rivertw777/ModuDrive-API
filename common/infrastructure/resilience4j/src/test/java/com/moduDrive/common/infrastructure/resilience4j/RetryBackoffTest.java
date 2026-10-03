package com.moduDrive.common.infrastructure.resilience4j;

import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The shared retry waits grow exponentially with jitter (spec 006 2-3-2). */
@SpringBootTest(
        classes = RetryBackoffTest.Config.class,
        properties = "spring.config.import=classpath:application-resilience4j.yml")
class RetryBackoffTest {

    @Autowired
    private RetryRegistry retryRegistry;

    @Test
    @DisplayName("재시도 간격은 500ms·1초를 기준으로 ±50% 무작위로 정해진다")
    void waitsGrowExponentiallyWithJitter() {
        IntervalBiFunction<Object> interval = retryRegistry.retry("anyRetry").getRetryConfig().getIntervalBiFunction();

        IntStream.range(0, 200).forEach(i -> {
            assertThat(interval.apply(1, null)).isBetween(250L, 750L);
            assertThat(interval.apply(2, null)).isBetween(500L, 1500L);
        });
        // Jittered: not one fixed value. Exponential: the second wait reaches past anything the first can be.
        assertThat(IntStream.range(0, 50).mapToLong(i -> interval.apply(1, null)).distinct().count())
                .isGreaterThan(1);
        assertThat(IntStream.range(0, 50).mapToLong(i -> interval.apply(2, null)).max().getAsLong())
                .isGreaterThan(750L);
    }

    @Configuration
    @EnableAutoConfiguration
    static class Config {
    }
}
