package com.moduDrive.gateway.security;

import com.moduDrive.common.api.auth.SessionCookie;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Drops a logged-out session from this instance's {@link SessionCheckCache} once auth-service has
 * answered — after, so a request racing the logout can't put it back. Other instances let it expire.
 */
@Component
@RequiredArgsConstructor
class LogoutSessionEvictionFilter implements WebFilter {

    static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final SessionCheckCache sessionCheckCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpCookie sessionCookie = request.getCookies().getFirst(SessionCookie.NAME);
        if (sessionCookie == null || !HttpMethod.POST.equals(request.getMethod())
                || !LOGOUT_PATH.equals(request.getPath().value())) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange).doFinally(signal -> sessionCheckCache.evict(sessionCookie.getValue()));
    }
}
