package com.moduDrive.common.infrastructure.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryMetricsTest {

    @Test
    @EnabledOnOs(OS.LINUX)
    void registersRssBelowLimit() {
        // given
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        // when
        new MemoryMetrics().bindTo(registry);

        // then
        double rss = registry.get("modudrive.memory.rss").gauge().value();
        double limit = registry.get("modudrive.memory.limit").gauge().value();
        assertThat(rss).isPositive().isLessThan(limit);
    }

    @Test
    void readsVmRssInBytes() {
        // given
        String status = """
                Name:\tjava
                VmPeak:\t 4000000 kB
                VmRSS:\t  512000 kB
                VmData:\t 3000000 kB
                """;

        // when
        double rss = MemoryMetrics.rss(status);

        // then
        assertThat(rss).isEqualTo(512000d * 1024);
    }

    @Test
    void isNanWithoutVmRss() {
        // when
        double rss = MemoryMetrics.rss("");

        // then
        assertThat(rss).isNaN();
    }
}
