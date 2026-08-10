package com.moduDrive.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;

@RequiredArgsConstructor
@EnableWebFluxSecurity
@Configuration
class SecurityConfig {

    private final ServerSecurityContextRepository securityContextRepository;
    private final ServerAuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/api/v1/member/sign-up").permitAll()
                        .pathMatchers("/api/v1/auth/login", "/api/v1/auth/reissue", "/api/v1/auth/logout").permitAll()
                        .pathMatchers("/webjars/swagger-ui/**", "/v3/api-docs/**", "/*/v3/api-docs/**").permitAll()
                        .anyExchange().authenticated()
                )
                .securityContextRepository(securityContextRepository)
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(authenticationEntryPoint)
                )
                .build();
    }
}
