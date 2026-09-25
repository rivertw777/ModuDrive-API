package com.moduDrive.gateway.adapter.in.web.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.api.dto.auth.SessionCookie;
import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.gateway.adapter.out.client.auth.AuthClient;
import com.moduDrive.gateway.exception.AuthExceptionCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
class CustomServerSecurityContextRepository implements ServerSecurityContextRepository {

    /** Sent by the WEB on polling requests: check the session without restarting its idle timeout. */
    static final String BACKGROUND_REQUEST_HEADER = "X-Background-Request";

    private final AuthClient authClient;
    private final ObjectMapper objectMapper;
    private final String sessionCookieName;

    CustomServerSecurityContextRepository(AuthClient authClient,
                                          ObjectMapper objectMapper,
                                          @Value("${session.cookie.secure}") boolean secureSessionCookie) {
        this.authClient = authClient;
        this.objectMapper = objectMapper;
        this.sessionCookieName = SessionCookie.name(secureSessionCookie);
    }

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        return Mono.empty();
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(sessionCookieName);
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, AuthExceptionCase.NO_SESSION);
            return Mono.empty();
        }

        // A client can only use this header to NOT extend its own session, so it is safe to trust.
        boolean touch = !"true".equalsIgnoreCase(
                exchange.getRequest().getHeaders().getFirst(BACKGROUND_REQUEST_HEADER));
        ValidateSessionRequest request = new ValidateSessionRequest(sessionCookie.getValue(), touch);

        return authClient.validateSession(request)
                .flatMap(apiResponse -> {
                    ValidateSessionResponse authData = apiResponse.getData();
                    if (authData == null) {
                        AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, AuthExceptionCase.UNAUTHORIZED);
                        return Mono.empty();
                    }
                    Authentication authentication = createAuthenticationToken(
                            authData.memberId(), authData.memberRoles());
                    return Mono.just((SecurityContext) new SecurityContextImpl(authentication));
                })
                .onErrorResume(WebClientResponseException.class, e -> handleWebClientException(exchange, e))
                .onErrorResume(Exception.class, e -> {
                    log.error("세션 확인 중 예상치 못한 오류 발생", e);
                    AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, AuthExceptionCase.UNAUTHORIZED);
                    return Mono.empty();
                });
    }

    private static Authentication createAuthenticationToken(String memberId, List<String> memberRoles) {
        List<GrantedAuthority> authorities = memberRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
        return new UsernamePasswordAuthenticationToken(memberId, null, authorities);
    }

    private Mono<SecurityContext> handleWebClientException(ServerWebExchange exchange, WebClientResponseException e) {
        try {
            String responseJson = e.getResponseBodyAsString();
            JsonNode jsonNode = objectMapper.readTree(responseJson);
            String rawStatus = jsonNode.path("status").asText(null);
            String status = isKnownHttpStatus(rawStatus)
                    ? rawStatus
                    : AuthExceptionCase.UNAUTHORIZED.getHttpStatus().name();
            String message = jsonNode.path("message").asText(AuthExceptionCase.UNAUTHORIZED.getMessage());
            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, status, message);
        } catch (Exception jsonProcessingException) {
            log.error("Content 파싱 실패 — HTTP {}", e.getStatusCode(), jsonProcessingException);
            AuthErrorAttributeUtils.setAuthErrorAttribute(exchange, AuthExceptionCase.UNAUTHORIZED);
        }
        return Mono.empty();
    }

    private static boolean isKnownHttpStatus(String status) {
        if (status == null) {
            return false;
        }
        try {
            org.springframework.http.HttpStatus.valueOf(status);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
