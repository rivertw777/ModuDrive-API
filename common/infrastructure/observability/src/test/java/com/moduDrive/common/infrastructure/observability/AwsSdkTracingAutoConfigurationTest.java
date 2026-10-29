package com.moduDrive.common.infrastructure.observability;

import io.awspring.cloud.autoconfigure.AwsSyncClientCustomizer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AwsSdkTracingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AwsSdkTracingAutoConfiguration.class));

    private static SesClientBuilder sesBuilder() {
        return SesClient.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(AnonymousCredentialsProvider.create());
    }

    @Test
    void addsTheTracingInterceptorWithoutDroppingAServicesOwnOverrides() {
        // given — a service's own customizer already set a timeout and turned retries off (mail-service's SesConfig)
        ApplicationContextRunner withOpenTelemetry = runner.withBean(OpenTelemetry.class, OpenTelemetry::noop);

        // when
        withOpenTelemetry.run(context -> {
            SesClientBuilder builder = sesBuilder();
            builder.overrideConfiguration(o -> o.apiCallTimeout(Duration.ofSeconds(7)).retryStrategy(r -> r.maxAttempts(1)));
            context.getBean(AwsSyncClientCustomizer.class).customize(builder);

            try (SesClient client = builder.build()) {
                ClientOverrideConfiguration override = client.serviceClientConfiguration().overrideConfiguration();

                // then
                assertThat(override.executionInterceptors())
                        .extracting(interceptor -> interceptor.getClass().getName())
                        .anyMatch(name -> name.startsWith("io.opentelemetry.instrumentation.awssdk"));
                assertThat(override.apiCallTimeout()).contains(Duration.ofSeconds(7));
                assertThat(override.retryStrategy()).hasValueSatisfying(retry -> assertThat(retry.maxAttempts()).isEqualTo(1));
            }
        });
    }

    @Test
    void addsNothingWithoutOpenTelemetry() {
        // when
        runner.run(context -> {
            // then
            assertThat(context).doesNotHaveBean(AwsSyncClientCustomizer.class);
        });
    }
}
