package com.moduDrive.gateway.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@RequiredArgsConstructor
@Configuration
class WebClientConfig {

    // Boot가 만들어주는 WebClient.Builder 빈을 직접 쓰지 않고 여기서 새로 만들기 때문에,
    // 트레이싱을 붙여주는 ObservationWebClientCustomizer를 수동으로 적용해야 gateway->auth
    // 호출에도 trace가 propagate 된다.
    private final List<WebClientCustomizer> customizers;

    @LoadBalanced
    @Bean
    WebClient.Builder loadBalancedWebClientBuilder() {
        WebClient.Builder builder = WebClient.builder();
        customizers.forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    @Bean
    WebClient authWebClient(@LoadBalanced WebClient.Builder builder) {
        return builder.baseUrl("lb://auth-service").build();
    }
}
