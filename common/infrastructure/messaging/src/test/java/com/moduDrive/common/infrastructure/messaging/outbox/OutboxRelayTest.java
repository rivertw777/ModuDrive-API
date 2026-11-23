package com.moduDrive.common.infrastructure.messaging.outbox;

import com.moduDrive.common.infrastructure.messaging.MessagePublisher;
import com.moduDrive.common.infrastructure.messaging.PermanentPublishException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
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
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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

    private final MessagePublisher messagePublisher = mock(MessagePublisher.class);
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
        relay = new OutboxRelay(entityManager, transactionTemplate, messagePublisher, jsonMapper, ObservationRegistry.NOOP,
                new OutboxMetrics(entityManager));
    }

    @Nested
    @DisplayName("이벤트를 기록할 때")
    class WhenRecording {

        @Test
        @DisplayName("호출자의 트랜잭션이 롤백되면 이벤트도 남지 않는다")
        void rollsBackWithTheCallersTransaction() {
            transactionTemplate.executeWithoutResult(status -> {
                recorder.record("queue", new OutboxTestEvent(UUID.randomUUID(), "a"));
                status.setRollbackOnly();
            });

            assertThat(rowCount()).isZero();
        }
    }

    @Nested
    @DisplayName("릴레이가 돌 때")
    class WhenRelaying {

        @Test
        @DisplayName("기록된 순서대로 큐별로 묶어 원래 이벤트 객체를 전송하고 행을 SENT로 남긴다")
        void sendsTheOriginalEventsInOrderThenMarksTheRowsSent() {
            OutboxTestEvent first = new OutboxTestEvent(UUID.randomUUID(), "first");
            OutboxTestEvent second = new OutboxTestEvent(UUID.randomUUID(), "second");
            OutboxTestEvent third = new OutboxTestEvent(UUID.randomUUID(), "third");
            recorder.record("queue-a", first);
            recorder.record("queue-b", second);
            recorder.record("queue-a", third);

            relay.relay();

            InOrder inOrder = inOrder(messagePublisher);
            inOrder.verify(messagePublisher).publishBatch(eq("queue-a"), payloads(first, third));
            inOrder.verify(messagePublisher).publishBatch(eq("queue-b"), payloads(second));
            assertThat(countByStatus(OutboxEventStatus.SENT)).isEqualTo(3);
            assertThat(countByStatus(OutboxEventStatus.PENDING)).isZero();
        }

        @Test
        @DisplayName("한 번에 SQS 한도인 10건씩만 묶어 보낸다")
        void sendsAtMostTenPerCall() {
            for (int i = 0; i < 25; i++) {
                recorder.record("queue", new OutboxTestEvent(UUID.randomUUID(), "e" + i));
            }

            relay.relay();

            assertThat(batches()).extracting(List::size).containsExactly(10, 10, 5);
            assertThat(countByStatus(OutboxEventStatus.SENT)).isEqualTo(25);
        }

        @Test
        @DisplayName("행 id를 중복 제거 id로 붙여, 재전송된 행을 컨슈머가 알아볼 수 있게 한다")
        void usesTheRowIdAsTheDeduplicationId() {
            recorder.record("queue", new OutboxTestEvent(UUID.randomUUID(), "a"));
            recorder.record("queue", new OutboxTestEvent(UUID.randomUUID(), "b"));

            relay.relay();

            assertThat(outgoing()).extracting(MessagePublisher.Outgoing::deduplicationId)
                    .allMatch(id -> id.matches("outbox-\\d+"))
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("묶음 중 일부만 일시 실패하면 나머지는 SENT, 실패한 행만 남기고 다음 묶음은 다음 틱으로 미룬다")
        void keepsOnlyTheTransientlyFailedRowsAndStopsTheTick() {
            OutboxTestEvent ok = new OutboxTestEvent(UUID.randomUUID(), "ok");
            OutboxTestEvent throttled = new OutboxTestEvent(UUID.randomUUID(), "throttled");
            recorder.record("queue-a", ok);
            recorder.record("queue-a", throttled);
            recorder.record("queue-b", new OutboxTestEvent(UUID.randomUUID(), "later"));
            givenBatchFailures(m -> m.payload().equals(throttled) ? new IllegalStateException("throttled") : null);

            relay.relay();

            then(messagePublisher).should(times(1)).publishBatch(anyString(), anyList());
            assertThat(countByStatus(OutboxEventStatus.SENT)).isEqualTo(1);
            assertThat(countByStatus(OutboxEventStatus.PENDING)).isEqualTo(2);
        }

        @Test
        @DisplayName("묶음 전송 자체가 실패하면 1건씩 보내고, 그것도 실패하면 그 행부터 남겨두고 멈춘다")
        void fallsBackToSingleSendsAndStopsAtTheFirstFailure() {
            recorder.record("queue", new OutboxTestEvent(UUID.randomUUID(), "first"));
            recorder.record("queue", new OutboxTestEvent(UUID.randomUUID(), "second"));
            givenBrokerDown();

            relay.relay();

            then(messagePublisher).should(times(1)).publish(anyString(), anyString(), any());
            assertThat(countByStatus(OutboxEventStatus.PENDING)).isEqualTo(2);
        }

        @Test
        @DisplayName("묶음이 통째로 거절되면(합계 1 MiB 초과 등) 1건씩 보내 문제 행만 격리하고 나머지는 보낸다")
        void findsTheRejectedRowOneByOneWhenTheWholeBatchIsRefused() {
            OutboxTestEvent tooLarge = new OutboxTestEvent(UUID.randomUUID(), "too-large");
            OutboxTestEvent next = new OutboxTestEvent(UUID.randomUUID(), "next");
            recorder.record("queue", tooLarge);
            recorder.record("queue", next);
            willThrow(new IllegalStateException("batch too long")).given(messagePublisher)
                    .publishBatch(anyString(), anyList());
            willAnswer(invocation -> {
                if (invocation.getArgument(2).equals(tooLarge)) {
                    throw new PermanentPublishException(new IllegalStateException("message too long"));
                }
                return null;
            }).given(messagePublisher).publish(anyString(), anyString(), any());

            relay.relay();

            assertThat(failedRow().getFailureReason()).contains("message too long");
            assertThat(countByStatus(OutboxEventStatus.SENT)).isEqualTo(1);
        }

        @Test
        @DisplayName("SQS가 그 메시지 자체를 거절하면(400) 그 행만 격리하고 뒤 행은 계속 보낸다")
        void parksARowSqsRejectsAndKeepsSending() {
            OutboxTestEvent rejected = new OutboxTestEvent(UUID.randomUUID(), "too-large");
            OutboxTestEvent next = new OutboxTestEvent(UUID.randomUUID(), "next");
            recorder.record("queue", rejected);
            recorder.record("queue", next);
            givenBatchFailures(m -> m.payload().equals(rejected)
                    ? new PermanentPublishException(new IllegalStateException("message rejected")) : null);

            relay.relay();
            relay.relay();

            assertThat(outgoing()).extracting(MessagePublisher.Outgoing::payload).containsExactly(rejected, next);
            assertThat(failedRow().getFailedAt()).isNotNull();
            assertThat(failedRow().getFailureReason()).startsWith(IllegalStateException.class.getSimpleName());
            assertThat(countByStatus(OutboxEventStatus.SENT)).isEqualTo(1);
        }

        @Test
        @DisplayName("실패 사유는 길어도 잘라서 남긴다 — SDK 메시지는 한 문단씩 오고, 알람에 실리는 건 앞부분뿐이다")
        void cutsALongFailureReasonDownToSize() {
            recorder.record("queue", new OutboxTestEvent(UUID.randomUUID(), "rejected"));
            givenBatchFailures(m -> new PermanentPublishException(new IllegalStateException("x".repeat(500))));

            relay.relay();

            assertThat(failedRow().getFailureReason())
                    .hasSize(OutboxEventJpaEntity.FAILURE_REASON_LENGTH);
        }

        @Test
        @DisplayName("복원할 수 없는 행은 실패로 표시해 이후 배치에서 빼고 나머지를 보낸다")
        void skipsARowThatCannotBeRebuilt() {
            transactionTemplate.executeWithoutResult(status -> entityManager.persist(
                    new OutboxEventJpaEntity("queue", "com.example.Gone", "{}", null)));
            OutboxTestEvent event = new OutboxTestEvent(UUID.randomUUID(), "ok");
            recorder.record("queue", event);

            relay.relay();
            relay.relay();

            assertThat(outgoing()).extracting(MessagePublisher.Outgoing::payload).containsExactly(event);
            assertThat(failedRow().getPayloadType()).isEqualTo("com.example.Gone");
            assertThat(failedRow().getFailedAt()).isNotNull();
            assertThat(failedRow().getFailureReason()).contains("com.example.Gone");
        }

        @Test
        @DisplayName("tracing이 없으면 메시지마다 기록한 요청의 trace 헤더를 그대로 붙인다")
        void attachesEachRecordingRequestsTraceToItsMessage() {
            recordTraced();

            relay.relay();

            assertThat(outgoing()).singleElement().satisfies(m ->
                    assertThat(m.traceHeaders()).containsEntry("traceparent", "00-trace-span-01"));
        }

        @Test
        @DisplayName("묶어 보낼 때도 기록한 요청의 trace 아래 relay span과 전송 span을 열고, 메시지엔 전송 span의 context를 싣는다")
        void sendsEachBatchedMessageUnderItsOwnSendSpan() {
            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(context -> true);
            // Stands in for the tracing handler: injects a context derived from the parent the send span got.
            registry.observationConfig().observationHandler(new ObservationHandler<SenderContext<Map<String, String>>>() {
                @Override
                public boolean supportsContext(Observation.Context context) {
                    return context instanceof SenderContext;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void onStart(SenderContext<Map<String, String>> context) {
                    ReceiverContext<Map<String, String>> relay =
                            (ReceiverContext<Map<String, String>>) context.getParentObservation().getContextView();
                    context.getSetter().set(context.getCarrier(), "traceparent",
                            "send-under-" + relay.getCarrier().get("traceparent"));
                }
            });

            recordTraced();
            new OutboxRelay(entityManager, transactionTemplate, messagePublisher, jsonMapper, registry,
                    new OutboxMetrics(entityManager)).relay();

            assertThat(outgoing()).singleElement().satisfies(m ->
                    assertThat(m.traceHeaders()).containsEntry("traceparent", "send-under-00-trace-span-01"));
        }

        @Test
        @DisplayName("1건씩 보낼 때는 기록한 요청의 trace 헤더로 Observation을 열어, 전송 중 현재 Observation이 되게 한다")
        void resumesTheRecordingRequestsTraceAsTheCurrentObservation() {
            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(context -> true);
            AtomicReference<Observation> currentDuringSend = new AtomicReference<>();
            willThrow(new IllegalStateException("batch refused")).given(messagePublisher)
                    .publishBatch(anyString(), anyList());
            willAnswer(invocation -> {
                currentDuringSend.set(registry.getCurrentObservation());
                return null;
            }).given(messagePublisher).publish(anyString(), anyString(), any());

            recordTraced();
            new OutboxRelay(entityManager, transactionTemplate, messagePublisher, jsonMapper, registry,
                    new OutboxMetrics(entityManager)).relay();

            // The publisher parents its send span on exactly this, wherever the client finishes the send.
            assertThat(currentDuringSend.get()).isNotNull();
            @SuppressWarnings("unchecked")
            ReceiverContext<Map<String, String>> context =
                    (ReceiverContext<Map<String, String>>) currentDuringSend.get().getContext();
            assertThat(context.getCarrier()).containsEntry("traceparent", "00-trace-span-01");
        }

        @Test
        @DisplayName("다른 인스턴스가 잠근 행은 기다리지 않고 건너뛴다")
        void skipsRowsLockedByAnotherInstance() throws Exception {
            recorder.record("queue", new OutboxTestEvent(UUID.randomUUID(), "locked"));
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
            then(messagePublisher).shouldHaveNoInteractions();
            assertThat(elapsedMillis).isLessThan(5_000);
            assertThat(countByStatus(OutboxEventStatus.PENDING)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("보낸 행을 정리할 때")
    class WhenPurging {

        @Test
        @DisplayName("보관 기간이 지난 SENT 행만 지우고, 최근 SENT·PENDING·FAILED 행은 남긴다")
        void deletesOnlySentRowsPastRetention() {
            recorder.record("old", new OutboxTestEvent(UUID.randomUUID(), "old"));
            recorder.record("recent", new OutboxTestEvent(UUID.randomUUID(), "recent"));
            relay.relay();
            transactionTemplate.executeWithoutResult(status -> entityManager
                    .createQuery("update OutboxEventJpaEntity e set e.sentAt = :old where e.queue = 'old'")
                    .setParameter("old", Instant.now().minus(OutboxRelay.SENT_RETENTION).minusSeconds(60))
                    .executeUpdate());
            recorder.record("pending", new OutboxTestEvent(UUID.randomUUID(), "pending"));
            transactionTemplate.executeWithoutResult(status -> entityManager.persist(
                    new OutboxEventJpaEntity("failed", "com.example.Gone", "{}", null)));
            givenBrokerDown();
            relay.relay();

            relay.purgeSent();

            List<String> left = transactionTemplate.execute(status -> entityManager
                    .createQuery("select e.queue from OutboxEventJpaEntity e order by e.id", String.class)
                    .getResultList());
            assertThat(left).containsExactly("recent", "pending", "failed");
        }
    }

    /** Records one event under a request whose trace context is {@code 00-trace-span-01}. */
    private void recordTraced() {
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
        new OutboxEventRecorder(entityManager, transactionTemplate, jsonMapper, tracer, propagator)
                .record("queue", new OutboxTestEvent(UUID.randomUUID(), "traced"));
    }

    /** Each batch call fails the messages {@code failure} returns an exception for; the rest are sent. */
    private void givenBatchFailures(Function<MessagePublisher.Outgoing, RuntimeException> failure) {
        willAnswer(invocation -> {
            Map<String, RuntimeException> failures = new HashMap<>();
            for (MessagePublisher.Outgoing message : invocation.<List<MessagePublisher.Outgoing>>getArgument(1)) {
                RuntimeException e = failure.apply(message);
                if (e != null) {
                    failures.put(message.deduplicationId(), e);
                }
            }
            return failures;
        }).given(messagePublisher).publishBatch(anyString(), anyList());
    }

    private void givenBrokerDown() {
        willThrow(new RuntimeException("broker down")).given(messagePublisher).publishBatch(anyString(), anyList());
        willThrow(new RuntimeException("broker down")).given(messagePublisher).publish(anyString(), anyString(), any());
    }

    private static List<MessagePublisher.Outgoing> payloads(Object... events) {
        return argThat(messages -> messages.stream().map(MessagePublisher.Outgoing::payload).toList()
                .equals(List.of(events)));
    }

    @SuppressWarnings("unchecked")
    private List<List<MessagePublisher.Outgoing>> batches() {
        ArgumentCaptor<List<MessagePublisher.Outgoing>> captor = ArgumentCaptor.forClass(List.class);
        then(messagePublisher).should(atLeastOnce()).publishBatch(anyString(), captor.capture());
        return captor.getAllValues();
    }

    private List<MessagePublisher.Outgoing> outgoing() {
        return batches().stream().flatMap(List::stream).toList();
    }

    @Nested
    @DisplayName("게이지는")
    class TheGauges {

        private final OutboxMetrics metrics = new OutboxMetrics(entityManager);

        @Test
        @DisplayName("보낼 행이 없으면 아무 큐도 내보내지 않는다")
        void publishesNothingWhenNothingIsWaiting() {
            assertThat(metrics.pendingLag()).isEmpty();
        }

        @Test
        @DisplayName("큐마다 가장 오래 기다린 행의 나이를 재고, 보내고 나면 그 큐가 사라진다 — 알람이 어느 큐가 막혔는지 말해야 한다")
        void measuresTheOldestWaitingRowPerQueueThenDropsTheQueue() {
            recorder.record("queue-a", new OutboxTestEvent(UUID.randomUUID(), "old"));
            recorder.record("queue-a", new OutboxTestEvent(UUID.randomUUID(), "new"));
            recorder.record("queue-b", new OutboxTestEvent(UUID.randomUUID(), "fresh"));
            backdateOldestPendingBy(Duration.ofMinutes(5));

            assertThat(metrics.pendingLag())
                    .anySatisfy(group -> {
                        assertThat(group.queue()).isEqualTo("queue-a");
                        assertThat(group.ageSeconds()).isGreaterThanOrEqualTo(300);
                    })
                    .anySatisfy(group -> {
                        assertThat(group.queue()).isEqualTo("queue-b");
                        assertThat(group.ageSeconds()).isLessThan(300);
                    });

            relay.relay();

            assertThat(countByStatus(OutboxEventStatus.PENDING)).isZero();
            assertThat(metrics.pendingLag()).isEmpty();
        }

        @Test
        @DisplayName("전송이 막혀 있으면 행이 PENDING으로 남아 나이가 올라간다 — 무한 재시도라 FAILED가 안 생기므로 이게 유일한 신호다")
        void keepsClimbingWhileSendingIsStuck() {
            givenBrokerDown();
            recorder.record("queue-a", new OutboxTestEvent(UUID.randomUUID(), "stuck"));
            backdateOldestPendingBy(Duration.ofMinutes(10));

            relay.relay();

            assertThat(countByStatus(OutboxEventStatus.FAILED)).isZero();
            assertThat(countByStatus(OutboxEventStatus.PENDING)).isEqualTo(1);
            assertThat(metrics.pendingLag()).singleElement().satisfies(group -> {
                assertThat(group.queue()).isEqualTo("queue-a");
                assertThat(group.ageSeconds()).isGreaterThanOrEqualTo(600);
            });
        }

        @Test
        @DisplayName("사람이 처리해야 하는 FAILED 행을 큐·사유별로 센다 — 알람이 어느 이벤트가 왜 멈췄는지 말해야 한다")
        void countsRowsParkedForAHumanByQueueAndReason() {
            assertThat(metrics.failedGroups()).isEmpty();
            recorder.record("queue-a", new OutboxTestEvent(UUID.randomUUID(), "rejected"));
            recorder.record("queue-a", new OutboxTestEvent(UUID.randomUUID(), "rejected"));
            recorder.record("queue-b", new OutboxTestEvent(UUID.randomUUID(), "rejected"));
            givenBatchFailures(m -> new PermanentPublishException(new IllegalStateException("message rejected")));

            relay.relay();

            assertThat(metrics.failedGroups()).containsExactlyInAnyOrder(
                    new OutboxMetrics.FailedGroup("queue-a", "IllegalStateException", "message rejected", 2),
                    new OutboxMetrics.FailedGroup("queue-b", "IllegalStateException", "message rejected", 1));
        }

        @Test
        @DisplayName("AWS SDK가 메시지 끝에 붙이는 요청 정보는 사유에서 뗀다 — 행마다 다른 Request ID가 라벨에 들어가면 행마다 시계열이 생긴다")
        void dropsTheAwsSdkRequestDetailsFromTheReason() {
            recorder.record("queue-a", new OutboxTestEvent(UUID.randomUUID(), "rejected"));
            recorder.record("queue-a", new OutboxTestEvent(UUID.randomUUID(), "rejected"));
            AtomicInteger requestId = new AtomicInteger();
            givenBatchFailures(m -> new PermanentPublishException(new IllegalStateException(
                    "The specified queue does not exist. (Service: Sqs, Status Code: 400, Request ID: r"
                            + requestId.incrementAndGet() + ")")));

            relay.relay();

            assertThat(metrics.failedGroups()).containsExactly(new OutboxMetrics.FailedGroup(
                    "queue-a", "IllegalStateException", "The specified queue does not exist.", 2));
        }

        /** The relay writes {@code created_at} itself, so waiting is simulated by moving it back. */
        private void backdateOldestPendingBy(Duration age) {
            transactionTemplate.executeWithoutResult(tx -> entityManager
                    .createQuery("update OutboxEventJpaEntity e set e.createdAt = :backdated "
                            + "where e.id = (select min(o.id) from OutboxEventJpaEntity o where o.status = :pending)")
                    .setParameter("backdated", Instant.now().minus(age))
                    .setParameter("pending", OutboxEventStatus.PENDING)
                    .executeUpdate());
        }
    }

    private long countByStatus(OutboxEventStatus status) {
        return transactionTemplate.execute(tx -> entityManager
                .createQuery("select count(e) from OutboxEventJpaEntity e where e.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult());
    }

    private OutboxEventJpaEntity failedRow() {
        return transactionTemplate.execute(tx -> entityManager
                .createQuery("select e from OutboxEventJpaEntity e where e.status = :failed", OutboxEventJpaEntity.class)
                .setParameter("failed", OutboxEventStatus.FAILED)
                .getSingleResult());
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
