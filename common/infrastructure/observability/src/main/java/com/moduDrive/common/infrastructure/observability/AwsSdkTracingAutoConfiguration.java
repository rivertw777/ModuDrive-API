package com.moduDrive.common.infrastructure.observability;

import io.awspring.cloud.autoconfigure.AwsSyncClientCustomizer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/**
 * Spans for the calls of every sync AWS client Spring Cloud AWS builds (SES's {@code SendRawEmail}, …),
 * which Micrometer doesn't see. On in a service that has Spring Cloud AWS and
 * {@code opentelemetry-aws-sdk-2.2} on its classpath. Sync only: the SQS client is async, and Spring Cloud
 * AWS already traces its sends and receives. A client built by hand (storage-service's {@code S3Config})
 * adds the interceptor itself.
 *
 * <p>A service's own customizer must add to {@code builder.overrideConfiguration().toBuilder()}: the
 * {@code overrideConfiguration(o -> …)} overload starts from scratch and silently drops this interceptor.
 */
@AutoConfiguration(after = OpenTelemetrySdkAutoConfiguration.class)
@ConditionalOnClass(name = {"io.awspring.cloud.autoconfigure.AwsSyncClientCustomizer",
        "io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry"})
@ConditionalOnBean(OpenTelemetry.class)
public class AwsSdkTracingAutoConfiguration {

    @Bean
    AwsSyncClientCustomizer awsSdkTracing(OpenTelemetry openTelemetry) {
        ExecutionInterceptor interceptor = AwsSdkTelemetry.create(openTelemetry).createExecutionInterceptor();
        // Added to the configuration already there, for the same reason. AwsSyncClientBuilder doesn't declare
        // overrideConfiguration; every client builder is also an SdkClientBuilder.
        return builder -> {
            if (builder instanceof SdkClientBuilder<?, ?> client) {
                client.overrideConfiguration(client.overrideConfiguration().toBuilder()
                        .addExecutionInterceptor(interceptor).build());
            }
        };
    }
}
