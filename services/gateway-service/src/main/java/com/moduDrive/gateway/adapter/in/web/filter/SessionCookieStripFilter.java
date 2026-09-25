package com.moduDrive.gateway.adapter.in.web.filter;

import com.moduDrive.common.api.dto.auth.SessionCookie;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Downstream services learn who the caller is from X_USER_ID alone, so the session cookie — a
 * live credential — has no business leaving the gateway except to auth-service, which needs it to
 * log in and out. Dropping it everywhere else keeps it out of other services' logs and bugs.
 */
@Component
class SessionCookieStripFilter implements GlobalFilter, Ordered {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final Set<String> SESSION_COOKIE_NAMES = Set.of(SessionCookie.SECURE_NAME, SessionCookie.INSECURE_NAME);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        boolean carriesSessionCookie = SESSION_COOKIE_NAMES.stream().anyMatch(request.getCookies()::containsKey);
        if (!carriesSessionCookie || request.getPath().value().startsWith(AUTH_PATH_PREFIX)) {
            return chain.filter(exchange);
        }

        String remainingCookies = request.getCookies().values().stream()
                .flatMap(List::stream)
                .filter(cookie -> !SESSION_COOKIE_NAMES.contains(cookie.getName()))
                .map(HttpCookie::toString)
                .collect(Collectors.joining("; "));
        ServerWebExchange stripped = exchange.mutate()
                .request(r -> r.headers(headers -> {
                    headers.remove(HttpHeaders.COOKIE);
                    if (!remainingCookies.isEmpty()) {
                        headers.set(HttpHeaders.COOKIE, remainingCookies);
                    }
                }))
                .build();
        return chain.filter(stripped);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
