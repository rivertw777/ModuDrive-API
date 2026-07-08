package com.moduDrive.gateway.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
class CustomAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return setErrorResponse(exchange);
    }

    private Mono<Void> setErrorResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = setErrorResponseBody(exchange, response);
        return response.writeWith(Mono.just(buffer));
    }

    private DataBuffer setErrorResponseBody(ServerWebExchange exchange, ServerHttpResponse response) {
        Tuple2<String, String> authErrorAttribute = AuthErrorAttributeUtils.getAuthErrorAttribute(exchange);

        ObjectNode jsonNode = objectMapper.createObjectNode();
        jsonNode.put("status", authErrorAttribute.getT1());
        jsonNode.put("message", authErrorAttribute.getT2());
        String responseBody = jsonNode.toString();

        DataBufferFactory bufferFactory = response.bufferFactory();
        return bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
    }
}
