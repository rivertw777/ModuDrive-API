package com.moduDrive.gateway.adapter.in.web.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * /logout and /reissue are permitAll (see SecurityConfig) and authenticate purely off the
 * refresh_token cookie — with SameSite=None in production (RefreshTokenCookieFactory) and CSRF
 * disabled globally, a cross-site auto-submitting form could POST to either and ride along on the
 * victim's cookie without ever needing CORS preflight. Requiring a custom header would need every
 * caller (including the SPA) to send one; checking Origin/Referer against the configured
 * clientUrl instead needs no client-side change — browsers attach Origin to state-changing
 * requests themselves, and don't let a page spoof it (#205).
 */
@Component
class CsrfOriginGuardFilter implements GlobalFilter, Ordered {

    private static final Set<String> PROTECTED_PATHS = Set.of("/api/v1/auth/logout", "/api/v1/auth/reissue");

    private final String clientUrl;

    CsrfOriginGuardFilter(@Value("${client.url}") String clientUrl) {
        this.clientUrl = clientUrl;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        boolean isProtected = request.getMethod() == HttpMethod.POST
                && PROTECTED_PATHS.contains(request.getPath().value());
        if (isProtected && !isFromAllowedOrigin(request)) {
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
        // Origin can be legitimately absent on some older/non-browser clients — Referer is a
        // fallback the request itself can't fabricate either, not a weaker substitute chosen for
        // convenience.
        String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
        return referer != null && referer.startsWith(clientUrl);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
