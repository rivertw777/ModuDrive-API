package com.moduDrive.gateway.adapter.out.client.auth;

import com.moduDrive.common.api.dto.auth.ValidateTokenRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

class AuthClientTest {

    @Nested
    @DisplayName("auth-service 응답이 멈춰 있을 때 (#206)")
    class WhenTheResponseStalls {

        @Test
        void failsWithTimeoutInsteadOfHangingForever() {
            // A response that starts but never completes — the same shape as an auth-service GC
            // pause or a stalled Redis lookup; without AuthClient's own .timeout(), this Mono
            // would simply never terminate.
            WebClient stallingClient = WebClient.builder()
                    .exchangeFunction(request -> Mono.never())
                    .build();
            AuthClient authClient = new AuthClient(stallingClient);

            StepVerifier.withVirtualTime(() -> authClient.validateToken(new ValidateTokenRequest("token")))
                    .thenAwait(Duration.ofSeconds(4))
                    .expectError(TimeoutException.class)
                    .verify();
        }
    }
}
