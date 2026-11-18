package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.config.AuditingConfig;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 006 2-5-1: the pool's connections carry the statement and lock-wait limits, and Postgres
 * cancelling a statement reaches the caller as the Spring exception GlobalExceptionHandler answers 503. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({FilePersistenceAdapter.class, FileMapper.class, AuditingConfig.class})
class StatementTimeoutTest {

    @Autowired private FilePersistenceAdapter adapter;
    @Autowired private SpringDataFileRepository fileRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    private final UUID namespaceId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery("delete from file where namespace_id = :namespaceId")
                        .setParameter("namespaceId", namespaceId).executeUpdate());
    }

    @Test
    @DisplayName("풀의 커넥션에는 쿼리 5초, 잠금 대기 3초 제한이 걸려 있다")
    void pooledConnectionsCarryTheLimits() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        String statement = tx.execute(status -> (String) entityManager.createNativeQuery("show statement_timeout").getSingleResult());
        String lock = tx.execute(status -> (String) entityManager.createNativeQuery("show lock_timeout").getSingleResult());

        assertThat(statement).isEqualTo("5s");
        assertThat(lock).isEqualTo("3s");
    }

    @Test
    @DisplayName("연쇄 작업용으로 늘린 쿼리 시간 제한은 그 트랜잭션에만 걸린다")
    void extendedTimeoutLastsOnlyForItsTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        String inside = tx.execute(status -> {
            adapter.extendStatementTimeout();
            return (String) entityManager.createNativeQuery("show statement_timeout").getSingleResult();
        });
        String after = tx.execute(status -> (String) entityManager.createNativeQuery("show statement_timeout").getSingleResult());

        assertThat(inside).isEqualTo("1min");
        assertThat(after).isEqualTo("5s");
    }

    @Test
    @DisplayName("버전 정리 후보 조회는 트랜잭션 밖에서 불려도 자기 트랜잭션에서 시간 제한을 늘린다")
    void pruneCandidateQueryExtendsInItsOwnTransaction() {
        // No caller transaction (the scheduler has none): set local would fail without the adapter's own.
        assertThatNoException().isThrownBy(() ->
                adapter.findFileIdsWithVersionsToPrune(LocalDateTime.now(), 5, new UUID(0, 0), 10));
    }

    @Test
    @DisplayName("잠금을 3초 넘게 기다리면 PessimisticLockingFailureException으로 끊긴다")
    void lockWaitPastTheLimitIsCancelled() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        holdRowLock(tx, () -> {
            long start = System.nanoTime();
            assertThatThrownBy(() -> tx.execute(status ->
                    adapter.lockActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "a.bin")))
                    .isInstanceOf(PessimisticLockingFailureException.class);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isGreaterThanOrEqualTo(2_500L);
        });
    }

    @Test
    @DisplayName("쿼리가 시간 제한을 넘으면 QueryTimeoutException으로 끊긴다")
    void statementPastTheLimitIsCancelled() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // The same lock wait, with the lock limit off and a short statement limit: Postgres cancels the
        // statement itself (57014), as it would a slow scan.
        holdRowLock(tx, () -> assertThatThrownBy(() -> tx.execute(status -> {
            entityManager.createNativeQuery("set local lock_timeout = 0").executeUpdate();
            entityManager.createNativeQuery("set local statement_timeout = '300ms'").executeUpdate();
            return adapter.lockActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "a.bin");
        })).isInstanceOf(QueryTimeoutException.class));
    }

    /** Runs {@code whileLocked} while another transaction holds a.bin's row lock. */
    private void holdRowLock(TransactionTemplate tx, Runnable whileLocked) throws Exception {
        UUID id = tx.execute(status -> fileRepository.save(
                new FileJpaEntity(namespaceId, "a.bin", "/", UUID.randomUUID(), FileStatus.UPLOADED, false)).getId());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery("select 1 from file where id = :id for update").setParameter("id", id).getResultList();
            locked.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            whileLocked.run();
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }
    }
}
