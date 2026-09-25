package com.moduDrive.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
class WebClientConfig {

    // Boot's WebClient.Builder already carries ObservationWebClientCustomizer, so gateway->auth
    // calls propagate the trace.
    @Bean
    WebClient authWebClient(WebClient.Builder builder, @Value("${clients.auth-service.url}") String authServiceUrl) {
        // Only the routed circuitBreaker filter had a TimeLimiter (15s) — this WebClient backs
        // SessionAuthenticationManager, which sits in front of every authenticated
        // request. With no client-level timeout, auth-service accepting a connection but never
        // responding (GC pause, Redis stall) hung every gateway request indefinitely; the circuit
        // breaker never saw a failure to open on (#206).
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(3, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(3, TimeUnit.SECONDS)));
        return builder.baseUrl(authServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
