package com.moduDrive.gateway.adapter.in.web.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
class PreFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Path only, not getURI() — the query string can carry a credential (e.g. storage-service's
        // streamToken), and logging every request at INFO would put every issued token in plaintext logs.
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().toString();
        log.info("Request URI: {}, Method: {}", path, method);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
