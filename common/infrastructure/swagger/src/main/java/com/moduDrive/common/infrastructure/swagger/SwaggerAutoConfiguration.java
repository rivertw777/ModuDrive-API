package com.moduDrive.common.infrastructure.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.stream.Collectors;

@Profile("dev")
@Configuration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SwaggerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI openAPI(@Value("${spring.application.name}") String applicationName) {
        // No security scheme: auth is the HttpOnly session cookie the browser already sends —
        // there's no token a Swagger user could paste in.
        return new OpenAPI()
                .addServersItem(new Server().url("/"))
                .info(new Info()
                        .title(toTitle(applicationName) + " API")
                        .version("1.0"));
    }

    private String toTitle(String applicationName) {
        return Arrays.stream(applicationName.split("-"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
