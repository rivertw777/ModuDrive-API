package com.moduDrive.gateway.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.common.infrastructure.resilience4j.CircuitBreakerExceptionCase;
import com.moduDrive.gateway.adapter.out.client.auth.AuthClient;
import com.moduDrive.gateway.exception.AuthExceptionCase;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Asks auth-service whether the session is alive. Every failure becomes a
 * {@link SessionAuthenticationException} — anything else would escape AuthenticationWebFilter as a 500:
 * 401 when auth-service rejected the session; when it couldn't be asked, the same answers a route
 * gives (2-1-3 of the resilience spec) — never a 401, which would send every user to the login screen (#447).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SessionAuthenticationManager implements ReactiveAuthenticationManager {

    private final AuthClient authClient;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        SessionAuthenticationToken token = (SessionAuthenticationToken) authentication;
        return authClient.validateSession(new ValidateSessionRequest(token.getCredentials(), token.touch()))
                .flatMap(apiResponse -> {
                    ValidateSessionResponse authData = apiResponse.getData();
                    if (authData == null) {
                        return Mono.error(new SessionAuthenticationException(AuthExceptionCase.UNAUTHORIZED));
                    }
                    return Mono.just(authenticated(authData.memberId(), authData.memberRoles()));
                })
                .onErrorMap(WebClientResponseException.class, this::fromAuthServiceResponse)
                // Open circuit, timeout, connect failure: auth-service didn't answer.
                .onErrorMap(e -> !(e instanceof AuthenticationException), e -> {
                    log.error("세션 확인 중 예상치 못한 오류 발생", e);
                    return new SessionAuthenticationException(unavailable(e));
                });
    }

    private static CircuitBreakerExceptionCase unavailable(Throwable e) {
        if (e instanceof CallNotPermittedException) {
            return CircuitBreakerExceptionCase.SERVICE_IS_OPEN;
        }
        if (e instanceof TimeoutException) {
            return CircuitBreakerExceptionCase.CONNECTION_TIMEOUT;
        }
        return CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE;
    }

    private static Authentication authenticated(String memberId, List<String> memberRoles) {
        List<GrantedAuthority> authorities = memberRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(memberId, null, authorities);
    }

    /** Only a 4xx is auth-service saying "no such session"; a 5xx means it couldn't check. */
    private SessionAuthenticationException fromAuthServiceResponse(WebClientResponseException e) {
        if (!e.getStatusCode().is4xxClientError()) {
            log.error("세션 확인 실패 — auth-service HTTP {}", e.getStatusCode());
            return new SessionAuthenticationException(e.getStatusCode().value() == 504
                    ? CircuitBreakerExceptionCase.CONNECTION_TIMEOUT
                    : CircuitBreakerExceptionCase.SERVICE_UNAVAILABLE);
        }
        try {
            String message = objectMapper.readTree(e.getResponseBodyAsString())
                    .path("message").asText(AuthExceptionCase.UNAUTHORIZED.getMessage());
            return new SessionAuthenticationException(AuthExceptionCase.UNAUTHORIZED.getHttpStatus().name(), message);
        } catch (Exception jsonProcessingException) {
            log.error("Content 파싱 실패 — HTTP {}", e.getStatusCode(), jsonProcessingException);
            return new SessionAuthenticationException(AuthExceptionCase.UNAUTHORIZED);
        }
    }
}
