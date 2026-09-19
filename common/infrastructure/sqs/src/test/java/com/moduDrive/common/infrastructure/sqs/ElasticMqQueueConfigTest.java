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
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Runs the real {@code .docker/elasticmq/elasticmq.conf} against the real SQS client stack and this
 * module's error handling, so a queue-name typo, a missing DLQ, a broken redrive policy or a
 * misclassified failure fails here instead of in the E2E. */
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
    @Autowired private SqsAsyncClient sqsAsyncClient;
    @Autowired private RecordingListener listener;

    @BeforeEach
    void reset() {
        listener.received.clear();
        listener.attempts.clear();
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
    @DisplayName("재처리 가능한 실패(일시 장애)가 계속될 때")
    class WhenARetryableFailureKeepsHappening {

        @Test
        @DisplayName("백오프하며 4번 시도한 뒤 DLQ로 옮겨지고, 재시도 소진 사유가 남는다")
        void retriesWithBackoffThenMovesToTheDlqWithTheReason() {
            MemberSignedUp event = new MemberSignedUp(UUID.randomUUID(), "retry@modudrive.com");

            send(event, "outbox-2");

            Message dead = awaitDeadLetter(event.email(), Duration.ofSeconds(45));
            assertThat(listener.attempts(event.email())).isEqualTo(4);
            assertThat(dead.messageAttributes().get(DeadLetteringErrorHandler.REASON_ATTRIBUTE).stringValue())
                    .startsWith("Retries exhausted after 4 attempts")
                    .contains("IllegalStateException: simulated outage");
        }
    }

    @Nested
    @DisplayName("재처리 불가능한 실패(잘못된 값)가 나면")
    class WhenAPermanentFailureHappens {

        @Test
        @DisplayName("재시도 없이 한 번 만에 DLQ로 옮겨지고, 원본 본문과 사유가 남는다")
        void movesStraightToTheDlqWithTheReason() {
            MemberSignedUp event = new MemberSignedUp(UUID.randomUUID(), "invalid@modudrive.com");

            send(event, "outbox-3");

            Message dead = awaitDeadLetter(event.email(), Duration.ofSeconds(10));
            assertThat(listener.attempts(event.email())).isEqualTo(1);
            assertThat(dead.body()).contains(event.memberId().toString());
            assertThat(dead.messageAttributes().get(DeadLetteringErrorHandler.REASON_ATTRIBUTE).stringValue())
                    .contains("IllegalArgumentException");
        }
    }

    @Nested
    @DisplayName("본문이 JSON으로 안 읽히는 메시지(포이즌 필)가 오면")
    class WhenThePayloadCannotBeRead {

        // Spring Cloud AWS converts the body in the message source, before the listener pipeline, and
        // on failure logs and drops it without calling the error handler. So a poison message can't
        // take the immediate path: it comes back after the queue's visibility timeout and the redrive
        // policy moves it after 4 receives (~40s), original body intact, without a reason attribute.
        @Test
        @DisplayName("리스너까지 가지 않고, 큐의 redrive로 원본 본문 그대로 DLQ에 옮겨진다")
        void movesTheRawBodyToTheDlqThroughRedrive() {
            String queueUrl = sqsAsyncClient.getQueueUrl(r -> r.queueName(MemberQueues.SIGNED_UP)).join().queueUrl();

            sqsAsyncClient.sendMessage(r -> r.queueUrl(queueUrl).messageBody("{not json")
                    .messageGroupId("poison").messageDeduplicationId("poison-1")).join();

            Message dead = awaitDeadLetter("{not json", Duration.ofSeconds(60));
            assertThat(dead.body()).isEqualTo("{not json");
            assertThat(listener.received).isEmpty();
        }
    }

    private void send(MemberSignedUp event, String deduplicationId) {
        sqsTemplate.send(MemberQueues.SIGNED_UP, MessageBuilder.withPayload(event)
                .setHeader(MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER, event.email())
                .setHeader(MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER, deduplicationId)
                .build());
    }

    /** Polls the DLQ until a message whose body contains {@code marker} shows up. */
    private Message awaitDeadLetter(String marker, Duration timeout) {
        String dlqUrl = sqsAsyncClient.getQueueUrl(r -> r.queueName(DLQ)).join().queueUrl();
        AtomicReference<Message> found = new AtomicReference<>();
        await().atMost(timeout).pollInterval(500, TimeUnit.MILLISECONDS).until(() -> {
            Optional<Message> match = sqsAsyncClient.receiveMessage(r -> r.queueUrl(dlqUrl)
                            .maxNumberOfMessages(10).waitTimeSeconds(1).messageAttributeNames("All"))
                    .join().messages().stream().filter(m -> m.body().contains(marker)).findFirst();
            match.ifPresent(found::set);
            return match.isPresent();
        });
        return found.get();
    }

    @EnableAutoConfiguration
    @Import(RecordingListener.class)
    static class TestApp {
    }

    @Component
    static class RecordingListener {

        final List<MemberSignedUp> received = new CopyOnWriteArrayList<>();
        final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

        int attempts(String email) {
            return attempts.getOrDefault(email, new AtomicInteger()).get();
        }

        @SqsListener(MemberQueues.SIGNED_UP)
        void onSignedUp(MemberSignedUp event) {
            attempts.computeIfAbsent(event.email(), e -> new AtomicInteger()).incrementAndGet();
            if (event.email().startsWith("retry")) {
                throw new IllegalStateException("simulated outage");
            }
            if (event.email().startsWith("invalid")) {
                throw new IllegalArgumentException("simulated bad payload");
            }
            received.add(event);
        }
    }
}
