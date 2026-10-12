package com.moduDrive.gateway.filter;

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
 * Rejects state-changing requests whose Origin isn't the client, login included (login CSRF).
 * Backs up SameSite=Strict on the session cookie. Browsers set Origin themselves and pages can't
 * spoof it, so the SPA needs no CSRF token (#205).
 *
 * <p>Runs before Spring Security, so a forged request never looks up (or refreshes) the session,
 * and after {@link TraceIdResponseFilter}, so the 403 still carries a trace id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
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
        // Some older clients omit Origin; Referer can't be forged by a page either. Match on a path
        // boundary so https://app.example.com.attacker.example doesn't pass as https://app.example.com.
        String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
        return referer != null && (referer.equals(clientUrl) || referer.startsWith(clientUrl + "/"));
    }
}
