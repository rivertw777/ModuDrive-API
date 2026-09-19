package com.moduDrive.common.infrastructure.sqs;

import com.moduDrive.common.event.member.MemberQueues;
import com.moduDrive.common.event.member.MemberSignedUp;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Runs the real {@code .docker/elasticmq/elasticmq.conf} against the real SQS client stack, so a
 * queue-name typo, a missing DLQ or a broken redrive policy fails here instead of in the E2E. */
@SpringBootTest(classes = ElasticMqQueueConfigTest.TestApp.class,
        properties = "spring.config.import=classpath:application-sqs.yml")
class ElasticMqQueueConfigTest {

    private static final String DLQ = "member-signed-up-dlq.fifo";

    // Not stopped by hand: Spring's cached context still polls it until the JVM exits, and
    // Testcontainers' reaper removes the container then.
    private static final GenericContainer<?> ELASTICMQ = new GenericContainer<>("softwaremill/elasticmq-native:1.7.1")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../../../.docker/elasticmq/elasticmq.conf").toAbsolutePath()),
                    "/opt/elasticmq.conf")
            .withExposedPorts(9324);

    @DynamicPropertySource
    static void sqsProperties(DynamicPropertyRegistry registry) {
        ELASTICMQ.start();
        registry.add("spring.cloud.aws.sqs.endpoint",
                () -> "http://" + ELASTICMQ.getHost() + ":" + ELASTICMQ.getMappedPort(9324));
        registry.add("spring.cloud.aws.region.static", () -> "ap-northeast-2");
        registry.add("spring.cloud.aws.credentials.access-key", () -> "local");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "local");
    }

    @Autowired private SqsTemplate sqsTemplate;
    @Autowired private RecordingListener listener;

    @BeforeEach
    void reset() {
        listener.received.clear();
        listener.attempts.set(0);
    }

    @Nested
    @DisplayName("FIFO 큐로 이벤트를 보낼 때")
    class WhenSending {

        @Test
        @DisplayName("같은 중복 제거 id로 두 번 보내도 리스너는 한 번만 받는다")
        void deliversOnceEvenWhenTheOutboxResends() {
            MemberSignedUp event = new MemberSignedUp(UUID.randomUUID(), "river@modudrive.com");

            send(event, "outbox-1");
            send(event, "outbox-1");

            await().atMost(Duration.ofSeconds(10)).until(() -> !listener.received.isEmpty());
            await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3))
                    .until(() -> listener.received.size() == 1);
            assertThat(listener.received).containsExactly(event);
        }
    }

    @Nested
    @DisplayName("리스너가 계속 실패할 때")
    class WhenTheListenerKeepsFailing {

        @Test
        @DisplayName("1회 시도 + 3회 재시도 뒤 DLQ로 옮겨진다")
        void movesTheMessageToTheDlqAfterFourReceives() {
            MemberSignedUp poison = new MemberSignedUp(UUID.randomUUID(), "fail@modudrive.com");

            send(poison, "outbox-2");

            await().atMost(Duration.ofSeconds(30)).pollInterval(1, TimeUnit.SECONDS).until(() ->
                    sqsTemplate.receive(from -> from.queue(DLQ).pollTimeout(Duration.ofSeconds(1)), MemberSignedUp.class)
                            .map(m -> m.getPayload().equals(poison)).orElse(false));
            assertThat(listener.attempts.get()).isEqualTo(4);
        }
    }

    private void send(MemberSignedUp event, String deduplicationId) {
        sqsTemplate.send(MemberQueues.SIGNED_UP, MessageBuilder.withPayload(event)
                .setHeader(MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, event.email())
                .setHeader(MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER, deduplicationId)
                .build());
    }

    @EnableAutoConfiguration
    @Import(RecordingListener.class)
    static class TestApp {
    }

    @Component
    static class RecordingListener {

        final List<MemberSignedUp> received = new CopyOnWriteArrayList<>();
        final AtomicInteger attempts = new AtomicInteger();

        // 1s instead of the queue's 10s visibility so the four failed receives take seconds.
        @SqsListener(value = MemberQueues.SIGNED_UP, messageVisibilitySeconds = "1")
        void onSignedUp(MemberSignedUp event) {
            if (event.email().startsWith("fail")) {
                attempts.incrementAndGet();
                throw new IllegalStateException("simulated consumer failure");
            }
            received.add(event);
        }
    }
}
