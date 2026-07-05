package com.moduDrive.member.adapter.in.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.http.HttpHeaders;

import java.util.Collections;

@Configuration
class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = HttpHeaders.AUTHORIZATION;

        return new OpenAPI()
                .addServersItem(new Server().url("/"))
                .info(new Info()
                        .title("Member Service API")
                        .version("1.0"))
                .components(
                        new Components()
                                .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .bearerFormat("JWT")
                                )
                )
                .security(Collections.singletonList(
                        new SecurityRequirement().addList(securitySchemeName)
                ));
    }
}
