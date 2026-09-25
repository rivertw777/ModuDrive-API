package com.moduDrive.gateway.adapter.in.web.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.gateway.adapter.out.client.auth.AuthClient;
import com.moduDrive.gateway.exception.AuthExceptionCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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

/**
 * Asks auth-service whether the session is alive. Every failure becomes a
 * {@link SessionAuthenticationException} — anything else would escape AuthenticationWebFilter as a 500.
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
                .onErrorMap(e -> !(e instanceof AuthenticationException), e -> {
                    log.error("세션 확인 중 예상치 못한 오류 발생", e);
                    return new SessionAuthenticationException(AuthExceptionCase.UNAUTHORIZED);
                });
    }

    private static Authentication authenticated(String memberId, List<String> memberRoles) {
        List<GrantedAuthority> authorities = memberRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(memberId, null, authorities);
    }

    private SessionAuthenticationException fromAuthServiceResponse(WebClientResponseException e) {
        try {
            JsonNode jsonNode = objectMapper.readTree(e.getResponseBodyAsString());
            String rawStatus = jsonNode.path("status").asText(null);
            String status = isKnownHttpStatus(rawStatus)
                    ? rawStatus
                    : AuthExceptionCase.UNAUTHORIZED.getHttpStatus().name();
            String message = jsonNode.path("message").asText(AuthExceptionCase.UNAUTHORIZED.getMessage());
            return new SessionAuthenticationException(status, message);
        } catch (Exception jsonProcessingException) {
            log.error("Content 파싱 실패 — HTTP {}", e.getStatusCode(), jsonProcessingException);
            return new SessionAuthenticationException(AuthExceptionCase.UNAUTHORIZED);
        }
    }

    private static boolean isKnownHttpStatus(String status) {
        if (status == null) {
            return false;
        }
        try {
            HttpStatus.valueOf(status);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
