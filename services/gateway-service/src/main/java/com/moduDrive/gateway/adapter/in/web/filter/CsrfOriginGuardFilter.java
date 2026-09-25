package com.moduDrive.gateway.adapter.in.web.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Every request is authenticated by the session cookie, which the browser attaches on its own —
 * so every state-changing request is a CSRF target, login included (logging a victim into the
 * attacker's account). SameSite=Strict is the first line; this is the second, in case that
 * attribute is ever loosened or ignored. Browsers set Origin on state-changing requests themselves
 * and don't let a page spoof it, so the SPA needs no CSRF token (#205).
 *
 * <p>A {@link WebFilter} ordered ahead of Spring Security, so a forged request is refused before
 * its cookie is ever looked up — it can't even refresh the session's idle timeout.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CsrfOriginGuardFilter implements WebFilter {

    private static final Set<HttpMethod> STATE_CHANGING_METHODS =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    private final String clientUrl;

    CsrfOriginGuardFilter(@Value("${client.url}") String clientUrl) {
        this.clientUrl = clientUrl;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (STATE_CHANGING_METHODS.contains(request.getMethod()) && !isFromAllowedOrigin(request)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private boolean isFromAllowedOrigin(ServerHttpRequest request) {
        String origin = request.getHeaders().getOrigin();
        if (origin != null) {
            return origin.equals(clientUrl);
        }
        // Origin can be legitimately absent on some older clients — Referer is a fallback the
        // request itself can't fabricate either. Match on a path boundary so
        // https://app.example.com.attacker.example doesn't pass as https://app.example.com.
        String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
        return referer != null && (referer.equals(clientUrl) || referer.startsWith(clientUrl + "/"));
    }
}
