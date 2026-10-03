package com.moduDrive.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class WebClientConfig {

    // Boot's WebClient.Builder already carries ObservationWebClientCustomizer, so gateway->auth
    // calls propagate the trace. No client-level timeouts: AuthClient's TimeLimiter (3s) bounds
    // the whole call, connect included, and starts first, so it always fires before they would.
    @Bean
    WebClient authWebClient(WebClient.Builder builder, @Value("${clients.auth-service.url}") String authServiceUrl) {
        return builder.baseUrl(authServiceUrl).build();
    }
}
