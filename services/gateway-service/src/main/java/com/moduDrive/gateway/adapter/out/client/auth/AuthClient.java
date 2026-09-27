package com.moduDrive.gateway.adapter.out.client.auth;

import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.common.core.web.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class AuthClient {

    private final WebClient authWebClient;

    public AuthClient(WebClient authWebClient) {
        this.authWebClient = authWebClient;
    }

    public Mono<ApiResponse<ValidateSessionResponse>> validateSession(ValidateSessionRequest request) {
        return authWebClient.post()
                .uri("/internal/v1/auth/sessions/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ValidateSessionResponse>>() {})
                // Backstop above the WebClient's own connect/read timeouts (WebClientConfig) — a
                // response that starts but stalls partway through (slow body write) is still
                // bounded here, so a caller waiting on this Mono can never hang indefinitely (#206).
                .timeout(Duration.ofSeconds(3));
    }
}
