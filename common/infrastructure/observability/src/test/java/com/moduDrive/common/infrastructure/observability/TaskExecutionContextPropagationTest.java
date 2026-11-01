package com.moduDrive.common.infrastructure.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.AsyncTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExecutionContextPropagationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.config.import=classpath:application-observability.yml")
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class));

    @Test
    void applicationTaskExecutorRunsTasksInTheCallersObservation() {
        // given — a request's observation open on the calling thread
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(context -> true);
        Observation request = Observation.start("http.server.requests", registry);

        runner.run(context -> {
            AsyncTaskExecutor executor = context.getBean("applicationTaskExecutor", AsyncTaskExecutor.class);

            // when — work handed to the executor, as Spring MVC does with a StreamingResponseBody
            Observation seenByTask;
            try (Observation.Scope ignored = request.openScope()) {
                seenByTask = executor.submit(registry::getCurrentObservation).get();
            } finally {
                request.stop();
            }

            // then
            assertThat(seenByTask).isSameAs(request);
        });
    }
}
