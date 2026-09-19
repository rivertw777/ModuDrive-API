package com.moduDrive.common.infrastructure.outbox;

import io.awspring.cloud.sqs.listener.SqsHeaders.MessageSystemAttributes;
import io.awspring.cloud.sqs.operations.SqsOperations;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sqs.model.SqsException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

/** Runs against real Postgres: {@code FOR UPDATE SKIP LOCKED} is the part that keeps two relay
 * instances from sending the same row, and H2 can't prove that. */
class OutboxRelayTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static ConfigurableApplicationContext context;
    private static EntityManager entityManager;
    private static TransactionTemplate transactionTemplate;
    private static JsonMapper jsonMapper;

    private final SqsOperations sqsOperations = mock(SqsOperations.class);
    private OutboxEventRecorder recorder;
    private OutboxRelay relay;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        context = new SpringApplicationBuilder(EntityScanConfig.class)
                .sources(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
                        TransactionAutoConfiguration.class, JacksonAutoConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.jpa.hibernate.ddl-auto=create-drop")
                .run();
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        jsonMapper = context.getBean(JsonMapper.class);
    }

    @AfterAll
    static void stopDatabase() {
        context.close();
        POSTGRES.stop();
    }

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery("delete from OutboxEventJpaEntity").executeUpdate());
        recorder = new OutboxEventRecorder(entityManager, transactionTemplate, jsonMapper, Tracer.NOOP, Propagator.NOOP);
        relay = new OutboxRelay(entityManager, transactionTemplate, sqsOperations, jsonMapper, ObservationRegistry.NOOP);
    }

    @Nested
    @DisplayName("이벤트를 기록할 때")
    class WhenRecording {

        @Test
        @DisplayName("호출자의 트랜잭션이 롤백되면 이벤트도 남지 않는다")
        void rollsBackWithTheCallersTransaction() {
            transactionTemplate.executeWithoutResult(status -> {
                recorder.record("topic", "key", new OutboxTestEvent(UUID.randomUUID(), "a"));
                status.setRollbackOnly();
            });

            assertThat(rowCount()).isZero();
        }
    }

    @Nested
    @DisplayName("릴레이가 돌 때")
    class WhenRelaying {

        @Test
        @DisplayName("기록된 순서대로 원래 이벤트 객체를 전송하고 행을 지운다")
        void sendsTheOriginalEventsInOrderThenDeletesTheRows() {
            OutboxTestEvent first = new OutboxTestEvent(UUID.randomUUID(), "first");
            OutboxTestEvent second = new OutboxTestEvent(UUID.randomUUID(), "second");
            recorder.record("queue-a.fifo", "k1", first);
            recorder.record("queue-b.fifo", "k2", second);

            relay.relay();

            List<Message<?>> sent = sentMessages();
            assertThat(sent).extracting(m -> (Object) m.getPayload()).containsExactly(first, second);
            assertThat(sent).extracting(m -> m.getHeaders().get(MessageSystemAttributes.SQS_MESSAGE_GROUP_ID_HEADER))
                    .containsExactly("k1", "k2");
            InOrder inOrder = inOrder(sqsOperations);
            inOrder.verify(sqsOperations).send(eq("queue-a.fifo"), any(Message.class));
            inOrder.verify(sqsOperations).send(eq("queue-b.fifo"), any(Message.class));
            assertThat(rowCount()).isZero();
        }

        @Test
        @DisplayName("행 id를 중복 제거 id로 붙여, 재전송돼도 SQS가 5분 안의 중복을 버리게 한다")
        void usesTheRowIdAsTheDeduplicationId() {
            recorder.record("queue.fifo", "k1", new OutboxTestEvent(UUID.randomUUID(), "a"));
            recorder.record("queue.fifo", "k1", new OutboxTestEvent(UUID.randomUUID(), "b"));

            relay.relay();

            assertThat(sentMessages()).extracting(m -> (String) m.getHeaders().get(MessageSystemAttributes.SQS_MESSAGE_DEDUPLICATION_ID_HEADER))
                    .allMatch(id -> id.matches("outbox-\\d+"))
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("전송이 실패하면 그 행부터 남겨두고 멈춘다")
        void stopsAtTheFirstFailedSendAndKeepsTheRest() {
            OutboxTestEvent first = new OutboxTestEvent(UUID.randomUUID(), "first");
            OutboxTestEvent second = new OutboxTestEvent(UUID.randomUUID(), "second");
            recorder.record("queue.fifo", "k1", first);
            recorder.record("queue.fifo", "k2", second);
            willThrow(new RuntimeException("sqs down")).given(sqsOperations).send(anyString(), any(Message.class));

            relay.relay();

            then(sqsOperations).should(times(1)).send(anyString(), any(Message.class));
            assertThat(rowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("SQS가 그 메시지 자체를 거절하면(400) 그 행만 격리하고 뒤 행은 계속 보낸다")
        void parksARowSqsRejectsAndKeepsSending() {
            OutboxTestEvent rejected = new OutboxTestEvent(UUID.randomUUID(), "too-large");
            OutboxTestEvent next = new OutboxTestEvent(UUID.randomUUID(), "next");
            recorder.record("queue.fifo", "k1", rejected);
            recorder.record("queue.fifo", "k2", next);
            SqsException invalid = (SqsException) SqsException.builder().statusCode(400)
                    .awsErrorDetails(AwsErrorDetails.builder().errorCode("InvalidParameterValue").build()).build();
            willAnswer(invocation -> {
                if (((Message<?>) invocation.getArgument(1)).getPayload().equals(rejected)) {
                    throw new RuntimeException("send failed", invalid);
                }
                return null;
            }).given(sqsOperations).send(anyString(), any(Message.class));

            relay.relay();
            relay.relay();

            assertThat(sentMessages()).extracting(m -> (Object) m.getPayload()).containsExactly(rejected, next);
            Instant failedAt = transactionTemplate.execute(status -> entityManager
                    .createQuery("select e from OutboxEventJpaEntity e", OutboxEventJpaEntity.class)
                    .getSingleResult().getFailedAt());
            assertThat(failedAt).isNotNull();
        }

        @Test
        @DisplayName("복원할 수 없는 행은 실패로 표시해 이후 배치에서 빼고 나머지를 보낸다")
        void skipsARowThatCannotBeRebuilt() {
            transactionTemplate.executeWithoutResult(status -> entityManager.persist(
                    new OutboxEventJpaEntity("topic", "k0", "com.example.Gone", "{}", null)));
            OutboxTestEvent event = new OutboxTestEvent(UUID.randomUUID(), "ok");
            recorder.record("topic", "k1", event);

            relay.relay();
            relay.relay();

            assertThat(sentMessages()).extracting(m -> (Object) m.getPayload()).containsExactly(event);
            assertThat(rowCount()).isEqualTo(1);
            Instant failedAt = transactionTemplate.execute(status -> entityManager
                    .createQuery("select e from OutboxEventJpaEntity e", OutboxEventJpaEntity.class)
                    .getSingleResult().getFailedAt());
            assertThat(failedAt).isNotNull();
        }

        @Test
        @DisplayName("기록한 요청의 trace 헤더로 Observation을 열어, 전송 중 현재 Observation이 되게 한다")
        void resumesTheRecordingRequestsTraceAsTheCurrentObservation() {
            Tracer tracer = mock(Tracer.class);
            Propagator propagator = mock(Propagator.class);
            Span requestSpan = mock(Span.class);
            TraceContext requestContext = mock(TraceContext.class);
            given(tracer.currentSpan()).willReturn(requestSpan);
            given(requestSpan.context()).willReturn(requestContext);
            willAnswer(invocation -> {
                Propagator.Setter<Object> setter = invocation.getArgument(2);
                setter.set(invocation.getArgument(1), "traceparent", "00-trace-span-01");
                return null;
            }).given(propagator).inject(eq(requestContext), any(), any());
            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(context -> true);
            AtomicReference<Observation> currentDuringSend = new AtomicReference<>();
            willAnswer(invocation -> {
                currentDuringSend.set(registry.getCurrentObservation());
                return null;
            }).given(sqsOperations).send(anyString(), any(Message.class));

            new OutboxEventRecorder(entityManager, transactionTemplate, jsonMapper, tracer, propagator)
                    .record("queue.fifo", "k1", new OutboxTestEvent(UUID.randomUUID(), "traced"));
            new OutboxRelay(entityManager, transactionTemplate, sqsOperations, jsonMapper, registry).relay();

            // SqsTemplate parents its send span on exactly this, including when it finishes on an SDK thread.
            assertThat(currentDuringSend.get()).isNotNull();
            @SuppressWarnings("unchecked")
            ReceiverContext<Map<String, String>> context =
                    (ReceiverContext<Map<String, String>>) currentDuringSend.get().getContext();
            assertThat(context.getCarrier()).containsEntry("traceparent", "00-trace-span-01");
        }

        @Test
        @DisplayName("다른 인스턴스가 잠근 행은 기다리지 않고 건너뛴다")
        void skipsRowsLockedByAnotherInstance() throws Exception {
            recorder.record("topic", "k1", new OutboxTestEvent(UUID.randomUUID(), "locked"));
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Thread otherInstance = Thread.ofVirtual().start(() -> transactionTemplate.executeWithoutResult(status -> {
                List<OutboxEventJpaEntity> rows = entityManager
                        .createQuery("select e from OutboxEventJpaEntity e", OutboxEventJpaEntity.class)
                        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                        .getResultList();
                assertThat(rows).hasSize(1);
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            long start = System.nanoTime();
            relay.relay();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            release.countDown();
            otherInstance.join();
            then(sqsOperations).shouldHaveNoInteractions();
            assertThat(elapsedMillis).isLessThan(5_000);
            assertThat(rowCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("FIFO 메시지 그룹 id를 만들 때")
    class WhenBuildingTheGroupId {

        @Test
        @DisplayName("128자를 넘는 키는 같은 키면 같은 값이 나오게 줄인다")
        void shortensKeysPastSqsLimitStably() {
            String longEmail = "a".repeat(200) + "@example.com";

            assertThat(OutboxRelay.groupId(longEmail)).hasSizeLessThanOrEqualTo(128)
                    .isEqualTo(OutboxRelay.groupId(longEmail));
            assertThat(OutboxRelay.groupId("river@modudrive.com")).isEqualTo("river@modudrive.com");
            assertThat(OutboxRelay.groupId(null)).isNotBlank();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Message<?>> sentMessages() {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        then(sqsOperations).should(atLeastOnce()).send(anyString(), captor.capture());
        return (List) captor.getAllValues();
    }

    private long rowCount() {
        return transactionTemplate.execute(status -> entityManager
                .createQuery("select count(e) from OutboxEventJpaEntity e", Long.class)
                .getSingleResult());
    }

    @Configuration
    @AutoConfigurationPackage
    static class EntityScanConfig {
    }
}
