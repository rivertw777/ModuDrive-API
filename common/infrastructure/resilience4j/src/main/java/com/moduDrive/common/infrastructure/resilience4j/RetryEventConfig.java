package com.moduDrive.common.infrastructure.resilience4j;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryOnErrorEvent;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/** Each retry is DEBUG; giving up after the last attempt is WARN (spec 006 3). */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(RetryRegistry.class)
public class RetryEventConfig {

    private final RetryRegistry retryRegistry;

    @PostConstruct
    public void registerRetryEventListeners() {
        retryRegistry.getAllRetries().forEach(this::listen);
        retryRegistry.getEventPublisher().onEntryAdded(event -> listen(event.getAddedEntry()));
    }

    private void listen(Retry retry) {
        retry.getEventPublisher()
                .onRetry(this::logRetry)
                .onError(this::logRetriesExhausted);
    }

    private void logRetry(RetryOnRetryEvent event) {
        log.debug("Retry for '{}' attempt number: {}",
                event.getName(),
                event.getNumberOfRetryAttempts());
    }

    private void logRetriesExhausted(RetryOnErrorEvent event) {
        log.warn("Retry '{}' gave up after {} attempts: {}",
                event.getName(),
                event.getNumberOfRetryAttempts(),
                event.getLastThrowable().toString());
    }
}
