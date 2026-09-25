package com.moduDrive.gateway.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@RequiredArgsConstructor
@EnableWebFluxSecurity
@Configuration
class SecurityConfig {

    private final ServerAuthenticationConverter sessionAuthenticationConverter;
    private final ReactiveAuthenticationManager sessionAuthenticationManager;
    private final ServerAuthenticationFailureHandler sessionAuthenticationFailureHandler;
    private final ServerAuthenticationEntryPoint authenticationEntryPoint;

    @Value("${client.url}")
    private String clientUrl;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/v1/member/verify-email/request", "/api/v1/member/verify-email/confirm", "/api/v1/member/sign-up").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/files/public/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/storage/public/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/storage/public/archive").permitAll()
                        .pathMatchers("/webjars/swagger-ui/**", "/v3/api-docs/**", "/*/v3/api-docs/**").permitAll()
                        // Actuator is served only on management.server.port (9464), which isn't published
                        // to the host — the public app port returns 404 for /actuator/** regardless. Network
                        // isolation is the guard here, so Prometheus can scrape without a token.
                        .pathMatchers("/actuator/**").permitAll()
                        .anyExchange().authenticated()
                )
                // Nothing to store between requests: the session lives in auth-service, checked once per
                // request by the filter below.
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .addFilterAt(sessionAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(authenticationEntryPoint)
                )
                .build();
    }

    private AuthenticationWebFilter sessionAuthenticationFilter() {
        AuthenticationWebFilter filter = new AuthenticationWebFilter(sessionAuthenticationManager);
        filter.setServerAuthenticationConverter(sessionAuthenticationConverter);
        filter.setAuthenticationFailureHandler(sessionAuthenticationFailureHandler);
        return filter;
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true); // the session cookie
        config.setAllowedOrigins(List.of(clientUrl));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
