package com.moduDrive.gateway.adapter.in.web.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Slf4j
@Component
class UserContextFilter implements GlobalFilter, Ordered {

    static final String HEADER_USER_ID = "X_USER_ID";
    static final String HEADER_USER_ROLE = "X_USER_ROLE";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Strip user context headers from all incoming requests to prevent client forgery.
        // Headers are repopulated only for authenticated requests via the security context.
        ServerWebExchange sanitized = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove(HEADER_USER_ID);
                    h.remove(HEADER_USER_ROLE);
                }))
                .build();

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated())
                .map(auth -> {
                    String userId = (String) auth.getPrincipal();
                    String roles = auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.joining(","));
                    return (ServerWebExchange) sanitized.mutate()
                            .request(r -> r.headers(headers -> {
                                headers.set(HEADER_USER_ID, userId);
                                headers.set(HEADER_USER_ROLE, roles);
                            }))
                            .build();
                })
                .defaultIfEmpty(sanitized)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
