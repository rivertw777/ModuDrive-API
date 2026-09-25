package com.moduDrive.gateway.adapter.out.client.auth;

import com.moduDrive.common.api.dto.auth.ValidateSessionRequest;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import com.moduDrive.common.core.web.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class AuthClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final WebClient authWebClient;
    private final String internalServiceToken;

    public AuthClient(WebClient authWebClient, @Value("${internal.service.token}") String internalServiceToken) {
        if (internalServiceToken == null || internalServiceToken.isBlank()) {
            // Fail at startup: with no secret every session check is refused by auth-service, which
            // would look like every user being logged out at once.
            throw new IllegalStateException("internal.service.token must be configured");
        }
        this.authWebClient = authWebClient;
        this.internalServiceToken = internalServiceToken;
    }

    public Mono<ApiResponse<ValidateSessionResponse>> validateSession(ValidateSessionRequest request) {
        return authWebClient.post()
                .uri("/internal/v1/auth/sessions/validate")
                .header(INTERNAL_TOKEN_HEADER, internalServiceToken)
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
