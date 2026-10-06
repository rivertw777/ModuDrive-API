package com.moduDrive.gateway.filter;

import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Hands every response its trace id, so a user reporting an error can quote it and the request's
 * logs and trace are one search away (#522). A {@link WebFilter} ahead of everything else, and set
 * just before commit, so responses written outside the route — a CSRF 403, an auth 401, a fallback —
 * carry it too.
 *
 * <p>Read from the exchange's server observation, not {@code Tracer.currentSpan()}: the reactive
 * chain doesn't keep the span on the thread that commits the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdResponseFilter implements WebFilter {

    static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            ServerRequestObservationContext.findCurrent(exchange.getAttributes())
                    .map(context -> context.<TracingContext>get(TracingContext.class))
                    .map(TracingContext::getSpan)
                    .ifPresent(span -> exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, span.context().traceId()));
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
