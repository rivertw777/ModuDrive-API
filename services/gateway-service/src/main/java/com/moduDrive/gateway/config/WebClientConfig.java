package com.moduDrive.gateway.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
class WebClientConfig {

    // Boot's WebClient.Builder already carries ObservationWebClientCustomizer, so gateway->auth
    // calls propagate the trace. No client-level timeouts: AuthClient's TimeLimiter (3s) bounds
    // the whole call, connect included, and starts first, so it always fires before they would.
    // Its own pool, not the routes' (spring.cloud.gateway...httpclient), with the same idle limit:
    // the task's Service Connect Envoy closes connections idle past its timeout.
    @Bean
    WebClient authWebClient(WebClient.Builder builder, @Value("${clients.auth-service.url}") String authServiceUrl) {
        ConnectionProvider pool = ConnectionProvider.builder("auth")
                .maxIdleTime(Duration.ofSeconds(30))
                .evictInBackground(Duration.ofSeconds(10))
                .build();
        return builder.baseUrl(authServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create(pool)))
                .build();
    }
}
