package com.moduDrive.mail.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SesConfigTest {

    @Test
    void keepsAnInterceptorAddedBeforeIt() {
        // given — Spring Cloud AWS applies the shared tracing customizer before this one
        ExecutionInterceptor tracing = new ExecutionInterceptor() {
        };
        SesClientBuilder builder = SesClient.builder().region(Region.AP_NORTHEAST_2)
                .credentialsProvider(AnonymousCredentialsProvider.create());
        builder.overrideConfiguration(o -> o.addExecutionInterceptor(tracing));

        // when
        new SesConfig().sesTimeouts().customize(builder);

        // then
        try (SesClient client = builder.build()) {
            ClientOverrideConfiguration override = client.serviceClientConfiguration().overrideConfiguration();
            assertThat(override.executionInterceptors()).contains(tracing);
            assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(7));
            assertThat(override.retryStrategy()).hasValueSatisfying(retry -> assertThat(retry.maxAttempts()).isEqualTo(1));
        }
    }
}
