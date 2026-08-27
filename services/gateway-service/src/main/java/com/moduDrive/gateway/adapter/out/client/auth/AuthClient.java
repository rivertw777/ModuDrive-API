package com.moduDrive.gateway.adapter.out.client.auth;

import com.moduDrive.common.api.dto.auth.ValidateTokenRequest;
import com.moduDrive.common.api.dto.auth.ValidateTokenResponse;
import com.moduDrive.common.core.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RequiredArgsConstructor
@Component
public class AuthClient {

    private final WebClient authWebClient;

    public Mono<ApiResponse<ValidateTokenResponse>> validateToken(ValidateTokenRequest request) {
        return authWebClient.post()
                .uri("/api/v1/auth/validate-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ValidateTokenResponse>>() {})
                // Backstop above the WebClient's own connect/read timeouts (WebClientConfig) — a
                // response that starts but stalls partway through (slow body write) is still
                // bounded here, so a caller waiting on this Mono can never hang indefinitely (#206).
                .timeout(Duration.ofSeconds(3));
    }
}
