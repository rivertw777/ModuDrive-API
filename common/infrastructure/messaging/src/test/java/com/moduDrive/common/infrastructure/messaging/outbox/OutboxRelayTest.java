package com.moduDrive.common.infrastructure.messaging.outbox;

import com.moduDrive.common.infrastructure.messaging.MessagePublisher;
import com.moduDrive.common.infrastructure.messaging.PermanentPublishException;
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
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
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
        relay = new OutboxRelay(entityManager, transactionTemplate, messagePublisher, jsonMapper, ObservationRegistry.NOOP);
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
        @DisplayName("기록된 순서대로 원래 이벤트 객체를 전송하고 행을 SENT로 남긴다")
        void sendsTheOriginalEventsInOrderThenMarksTheRowsSent() {
            OutboxTestEvent first = new OutboxTestEvent(UUID.randomUUID(), "first");
            OutboxTestEvent second = new OutboxTestEvent(UUID.randomUUID(), "second");
            recorder.record("queue-a.fifo", "k1", first);
            recorder.record("queue-b.fifo", "k2", second);

            relay.relay();

            InOrder inOrder = inOrder(messagePublisher);
            inOrder.verify(messagePublisher).publish(eq("queue-a.fifo"), eq("k1"), anyString(), eq(first));
            inOrder.verify(messagePublisher).publish(eq("queue-b.fifo"), eq("k2"), anyString(), eq(second));
            assertThat(countByStatus(OutboxEventStatus.SENT)).isEqualTo(2);
            assertThat(countByStatus(OutboxEventStatus.PENDING)).isZero();
        }

        @Test
        @DisplayName("행 id를 중복 제거 id로 붙여, 재전송돼도 SQS가 5분 안의 중복을 버리게 한다")
        void usesTheRowIdAsTheDeduplicationId() {
            recorder.record("queue.fifo", "k1", new OutboxTestEvent(UUID.randomUUID(), "a"));
            recorder.record("queue.fifo", "k1", new OutboxTestEvent(UUID.randomUUID(), "b"));

            relay.relay();

            assertThat(deduplicationIds())
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
            willThrow(new RuntimeException("broker down")).given(messagePublisher)
                    .publish(anyString(), anyString(), anyString(), any());

            relay.relay();

            then(messagePublisher).should(times(1)).publish(anyString(), anyString(), anyString(), any());
            assertThat(countByStatus(OutboxEventStatus.PENDING)).isEqualTo(2);
        }

        @Test
        @DisplayName("SQS가 그 메시지 자체를 거절하면(400) 그 행만 격리하고 뒤 행은 계속 보낸다")
        void parksARowSqsRejectsAndKeepsSending() {
            OutboxTestEvent rejected = new OutboxTestEvent(UUID.randomUUID(), "too-large");
            OutboxTestEvent next = new OutboxTestEvent(UUID.randomUUID(), "next");
            recorder.record("queue.fifo", "k1", rejected);
            recorder.record("queue.fifo", "k2", next);
            willAnswer(invocation -> {
                if (invocation.getArgument(3).equals(rejected)) {
                    throw new PermanentPublishException(new IllegalStateException("message rejected"));
                }
                return null;
            }).given(messagePublisher).publish(anyString(), anyString(), anyString(), any());

            relay.relay();
            relay.relay();

            assertThat(publishedPayloads()).containsExactly(rejected, next);
            assertThat(failedRow().getFailedAt()).isNotNull();
            assertThat(failedRow().getFailureReason()).startsWith(IllegalStateException.class.getName());
            assertThat(countByStatus(OutboxEventStatus.SENT)).isEqualTo(1);
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

            assertThat(publishedPayloads()).containsExactly(event);
            assertThat(failedRow().getPayloadType()).isEqualTo("com.example.Gone");
            assertThat(failedRow().getFailedAt()).isNotNull();
            assertThat(failedRow().getFailureReason()).contains("com.example.Gone");
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
            }).given(messagePublisher).publish(anyString(), anyString(), anyString(), any());

            new OutboxEventRecorder(entityManager, transactionTemplate, jsonMapper, tracer, propagator)
                    .record("queue.fifo", "k1", new OutboxTestEvent(UUID.randomUUID(), "traced"));
            new OutboxRelay(entityManager, transactionTemplate, messagePublisher, jsonMapper, registry).relay();

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
            recorder.record("queue.fifo", "old", new OutboxTestEvent(UUID.randomUUID(), "old"));
            recorder.record("queue.fifo", "recent", new OutboxTestEvent(UUID.randomUUID(), "recent"));
            relay.relay();
            transactionTemplate.executeWithoutResult(status -> entityManager
                    .createQuery("update OutboxEventJpaEntity e set e.sentAt = :old where e.messageKey = 'old'")
                    .setParameter("old", Instant.now().minus(OutboxRelay.SENT_RETENTION).minusSeconds(60))
                    .executeUpdate());
            recorder.record("queue.fifo", "pending", new OutboxTestEvent(UUID.randomUUID(), "pending"));
            transactionTemplate.executeWithoutResult(status -> entityManager.persist(
                    new OutboxEventJpaEntity("queue.fifo", "failed", "com.example.Gone", "{}", null)));
            willThrow(new RuntimeException("broker down")).given(messagePublisher)
                    .publish(anyString(), anyString(), anyString(), any());
            relay.relay();

            relay.purgeSent();

            List<String> left = transactionTemplate.execute(status -> entityManager
                    .createQuery("select e.messageKey from OutboxEventJpaEntity e order by e.id", String.class)
                    .getResultList());
            assertThat(left).containsExactly("recent", "pending", "failed");
        }
    }

    private List<Object> publishedPayloads() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        then(messagePublisher).should(atLeastOnce())
                .publish(anyString(), anyString(), anyString(), captor.capture());
        return captor.getAllValues();
    }

    private List<String> deduplicationIds() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        then(messagePublisher).should(atLeastOnce())
                .publish(anyString(), anyString(), captor.capture(), any());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("적체를 재는 게이지는")
    class TheLagGauge {

        private final OutboxLag lag = new OutboxLag(entityManager);

        @Test
        @DisplayName("보낼 행이 없으면 0이다")
        void readsZeroWhenNothingIsWaiting() {
            assertThat(lag.oldestPendingAgeSeconds()).isZero();
        }

        @Test
        @DisplayName("가장 오래 기다린 행의 나이를 재고, 보내고 나면 0으로 돌아온다")
        void measuresTheOldestWaitingRowThenFallsBackToZero() {
            recorder.record("queue-a.fifo", "k1", new OutboxTestEvent(UUID.randomUUID(), "old"));
            recorder.record("queue-a.fifo", "k2", new OutboxTestEvent(UUID.randomUUID(), "new"));
            backdateOldestPendingBy(Duration.ofMinutes(5));

            assertThat(lag.oldestPendingAgeSeconds()).isGreaterThanOrEqualTo(300);

            relay.relay();

            assertThat(countByStatus(OutboxEventStatus.PENDING)).isZero();
            assertThat(lag.oldestPendingAgeSeconds()).isZero();
        }

        @Test
        @DisplayName("전송이 막혀 있으면 행이 PENDING으로 남아 나이가 올라간다 — 무한 재시도라 FAILED가 안 생기므로 이게 유일한 신호다")
        void keepsClimbingWhileSendingIsStuck() {
            willThrow(new IllegalStateException("sqs unreachable"))
                    .given(messagePublisher).publish(anyString(), anyString(), anyString(), any());
            recorder.record("queue-a.fifo", "k1", new OutboxTestEvent(UUID.randomUUID(), "stuck"));
            backdateOldestPendingBy(Duration.ofMinutes(10));

            relay.relay();

            assertThat(countByStatus(OutboxEventStatus.FAILED)).isZero();
            assertThat(countByStatus(OutboxEventStatus.PENDING)).isEqualTo(1);
            assertThat(lag.oldestPendingAgeSeconds()).isGreaterThanOrEqualTo(600);
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
