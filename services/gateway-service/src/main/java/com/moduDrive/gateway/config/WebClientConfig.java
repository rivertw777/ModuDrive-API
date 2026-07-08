package com.moduDrive.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class WebClientConfig {

    @Bean
    WebClient authWebClient(WebClient.Builder builder) {
        return builder.baseUrl("lb://auth-service").build();
    }
}
