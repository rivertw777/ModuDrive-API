package com.moduDrive.common.infrastructure.resilience4j;

import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The error decoder of every Feign client (spec 006 2-3-3). */
@Configuration
@ConditionalOnClass(ErrorDecoder.class)
public class FeignErrorDecoderConfig {

    @Bean
    ErrorDecoder dependencyUnavailableErrorDecoder() {
        return new DependencyUnavailableErrorDecoder();
    }
}
