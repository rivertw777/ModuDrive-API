package com.moduDrive.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class WebClientConfig {

    @LoadBalanced
    @Bean
    WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    WebClient authWebClient(@LoadBalanced WebClient.Builder builder) {
        return builder.baseUrl("lb://auth-service").build();
    }
}
