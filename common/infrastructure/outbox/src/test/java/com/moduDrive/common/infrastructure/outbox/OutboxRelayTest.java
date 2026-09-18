package com.moduDrive.common.infrastructure.outbox;

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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

/** Runs against real Postgres: {@code FOR UPDATE SKIP LOCKED} is the part that keeps two relay
 * instances from sending the same row, and H2 can't prove that. */
class OutboxRelayTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final String SOURCE = "member-service";

    private static ConfigurableApplicationContext context;
    private static EntityManager entityManager;
    private static TransactionTemplate transactionTemplate;
    private static JsonMapper jsonMapper;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
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
        recorder = new OutboxEventRecorder(SOURCE, entityManager, transactionTemplate, jsonMapper, Tracer.NOOP, Propagator.NOOP);
        relay = new OutboxRelay(SOURCE, entityManager, transactionTemplate, kafkaTemplate, jsonMapper, Tracer.NOOP, Propagator.NOOP);
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
            recorder.record("topic-a", "k1", first);
            recorder.record("topic-b", "k2", second);
            given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(CompletableFuture.completedFuture(null));

            relay.relay();

            InOrder inOrder = inOrder(kafkaTemplate);
            inOrder.verify(kafkaTemplate).send("topic-a", "k1", first);
            inOrder.verify(kafkaTemplate).send("topic-b", "k2", second);
            assertThat(rowCount()).isZero();
        }

        @Test
        @DisplayName("전송이 실패하면 그 행부터 남겨두고 멈춘다")
        void stopsAtTheFirstFailedSendAndKeepsTheRest() {
            OutboxTestEvent first = new OutboxTestEvent(UUID.randomUUID(), "first");
            OutboxTestEvent second = new OutboxTestEvent(UUID.randomUUID(), "second");
            recorder.record("topic", "k1", first);
            recorder.record("topic", "k2", second);
            given(kafkaTemplate.send(anyString(), anyString(), any()))
                    .willReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

            relay.relay();

            then(kafkaTemplate).should(never()).send("topic", "k2", second);
            assertThat(rowCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("복원할 수 없는 행은 건너뛰고 나머지를 보낸다")
        void skipsARowThatCannotBeRebuilt() {
            transactionTemplate.executeWithoutResult(status -> entityManager.persist(
                    new OutboxEventJpaEntity(SOURCE, "topic", "k0", "com.example.Gone", "{}", null)));
            OutboxTestEvent event = new OutboxTestEvent(UUID.randomUUID(), "ok");
            recorder.record("topic", "k1", event);
            given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(CompletableFuture.completedFuture(null));

            relay.relay();

            then(kafkaTemplate).should().send("topic", "k1", event);
            assertThat(rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("기록한 요청의 trace 헤더를 전송 시점에 다시 이어붙인다")
        @SuppressWarnings("unchecked")
        void resumesTheRecordingRequestsTrace() {
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
            Span.Builder builder = mock(Span.Builder.class);
            Span relaySpan = mock(Span.class);
            given(propagator.extract(any(), any())).willReturn(builder);
            given(builder.name(anyString())).willReturn(builder);
            given(builder.start()).willReturn(relaySpan);
            given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(CompletableFuture.completedFuture(null));

            new OutboxEventRecorder(SOURCE, entityManager, transactionTemplate, jsonMapper, tracer, propagator)
                    .record("topic", "k1", new OutboxTestEvent(UUID.randomUUID(), "traced"));
            new OutboxRelay(SOURCE, entityManager, transactionTemplate, kafkaTemplate, jsonMapper, tracer, propagator)
                    .relay();

            ArgumentCaptor<Map<String, String>> carrier = ArgumentCaptor.forClass(Map.class);
            then(propagator).should().extract(carrier.capture(), any());
            assertThat(carrier.getValue()).containsEntry("traceparent", "00-trace-span-01");
            then(tracer).should().withSpan(relaySpan);
            then(relaySpan).should().end();
        }

        @Test
        @DisplayName("같은 DB를 쓰는 다른 서비스가 기록한 행은 건드리지 않는다")
        void leavesRowsRecordedByAnotherService() {
            new OutboxEventRecorder("file-service", entityManager, transactionTemplate, jsonMapper, Tracer.NOOP, Propagator.NOOP)
                    .record("topic", "k1", new OutboxTestEvent(UUID.randomUUID(), "theirs"));

            relay.relay();

            then(kafkaTemplate).shouldHaveNoInteractions();
            assertThat(rowCount()).isEqualTo(1);
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
            then(kafkaTemplate).shouldHaveNoInteractions();
            assertThat(elapsedMillis).isLessThan(5_000);
            assertThat(rowCount()).isEqualTo(1);
        }
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
