package com.moduDrive.common.infrastructure.observability;

import com.sun.management.OperatingSystemMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The process's resident memory against the container's limit — off-heap (metaspace, threads, buffers)
 * included, which the JVM meters leave out. Past the limit the task is OOM-killed, not slowed. RSS, not the
 * cgroup's usage: that counts the page cache, which the kernel takes back before killing anything.
 */
class MemoryMetrics implements MeterBinder {

    private static final Path STATUS = Path.of("/proc/self/status");

    @Override
    public void bindTo(MeterRegistry registry) {
        if (!Files.isReadable(STATUS)) {
            return; // not Linux: a service run from the IDE on a Mac
        }
        Gauge.builder("modudrive.memory.rss", () -> rss(read()))
                .baseUnit("bytes")
                .description("Resident memory of the process, off-heap included")
                .register(registry);
        // The container's limit (cgroup), or the machine's memory when there is none — on Fargate the task's.
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Gauge.builder("modudrive.memory.limit", os, OperatingSystemMXBean::getTotalMemorySize)
                .baseUnit("bytes")
                .description("Memory the container may use")
                .register(registry);
    }

    /** {@code VmRSS:   123456 kB} from /proc/self/status, in bytes. */
    static double rss(String status) {
        return status.lines()
                .filter(line -> line.startsWith("VmRSS:"))
                .mapToDouble(line -> Long.parseLong(line.replaceAll("\\D", "")) * 1024)
                .findFirst()
                .orElse(Double.NaN);
    }

    private static String read() {
        try {
            return Files.readString(STATUS);
        } catch (IOException e) {
            return "";
        }
    }
}
