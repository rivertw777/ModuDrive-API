package com.moduDrive.common.infrastructure.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LogExportAutoConfigurationTest {

    private final Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory())
            .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogExportAutoConfiguration.class));

    @AfterEach
    void detach() {
        root.detachAppender(LogExportAutoConfiguration.APPENDER_NAME);
    }

    @Test
    void attachesAppenderToRootLoggerWhenOpenTelemetryExists() {
        // given
        ApplicationContextRunner withOpenTelemetry = runner.withBean(OpenTelemetry.class, OpenTelemetry::noop);

        // when
        withOpenTelemetry.run(context -> {
            // then
            assertThat(root.getAppender(LogExportAutoConfiguration.APPENDER_NAME)).isNotNull();
        });
    }

    @Test
    void attachesNothingWithoutOpenTelemetry() {
        // when
        runner.run(context -> {
            // then
            assertThat(root.getAppender(LogExportAutoConfiguration.APPENDER_NAME)).isNull();
        });
    }
}
