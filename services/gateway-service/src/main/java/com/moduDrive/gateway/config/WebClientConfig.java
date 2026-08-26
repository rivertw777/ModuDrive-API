package com.moduDrive.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

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
        // Only the routed circuitBreaker filter had a TimeLimiter (15s) — this WebClient backs
        // CustomServerSecurityContextRepository, which sits in front of every authenticated
        // request. With no client-level timeout, auth-service accepting a connection but never
        // responding (GC pause, Redis stall) hung every gateway request indefinitely; the circuit
        // breaker never saw a failure to open on (#206).
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(3, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(3, TimeUnit.SECONDS)));
        return builder.baseUrl("lb://auth-service")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
