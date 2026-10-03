package com.moduDrive.common.infrastructure.resilience4j;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * Logs what a person reading the logs needs (spec 006 3): a circuit opening is WARN, everything else
 * quieter. Counting failures is the metrics' job (resilience4j-micrometer), not one log line per call.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(CircuitBreakerRegistry.class)
public class CircuitBreakerEventConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    public void registerCircuitBreakerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::listen);
        // Instances created after startup (first use of a name not in the yml) get the same listeners.
        circuitBreakerRegistry.getEventPublisher().onEntryAdded(event -> listen(event.getAddedEntry()));
    }

    private void listen(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(this::logStateTransition)
                .onError(this::logErrorEvent);
    }

    private void logStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        CircuitBreaker.State to = event.getStateTransition().getToState();
        String message = "CircuitBreaker '{}' state changed from {} to {}";
        if (to == CircuitBreaker.State.OPEN || to == CircuitBreaker.State.FORCED_OPEN) {
            log.warn(message, event.getCircuitBreakerName(), event.getStateTransition().getFromState(), to);
        } else {
            log.info(message, event.getCircuitBreakerName(), event.getStateTransition().getFromState(), to);
        }
    }

    private void logErrorEvent(CircuitBreakerOnErrorEvent event) {
        log.debug("CircuitBreaker '{}' recorded an error: {}",
                event.getCircuitBreakerName(),
                event.getThrowable().toString());
    }
}
