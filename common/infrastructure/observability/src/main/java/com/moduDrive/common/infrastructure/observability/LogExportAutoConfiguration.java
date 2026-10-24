package com.moduDrive.common.infrastructure.observability;

import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Sends every log line to the OTel collector as well as stdout. Boot builds the OTLP log exporter
 * ({@code management.opentelemetry.logging.export.otlp.endpoint}) but leaves bridging Logback to it to us.
 * Added to the root logger once the {@link OpenTelemetry} bean exists, so the few lines before that reach
 * stdout only. The record carries the current span's trace id, and {@code userId} from the MDC.
 */
@AutoConfiguration(after = OpenTelemetrySdkAutoConfiguration.class)
@ConditionalOnBean(OpenTelemetry.class)
public class LogExportAutoConfiguration {

    static final String APPENDER_NAME = "OTEL";

    @Bean
    InitializingBean openTelemetryLogAppender(OpenTelemetry openTelemetry) {
        return () -> {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            OpenTelemetryAppender appender = new OpenTelemetryAppender();
            appender.setName(APPENDER_NAME);
            appender.setContext(context);
            appender.setOpenTelemetry(openTelemetry);
            appender.setCaptureMdcAttributes(UserIdObservationFilter.MDC_KEY);
            appender.start();
            context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
        };
    }
}
