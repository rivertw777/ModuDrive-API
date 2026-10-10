package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.config.AuditingConfig;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** The commit's row lock against a concurrent trash, on real Postgres with real transactions — the
 * slice's per-test rollback transaction is off, so each step commits and the rows are cleaned up. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({FilePersistenceAdapter.class, FileMapper.class, AuditingConfig.class})
class FileRowLockTest {

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
    @DisplayName("commit이 잠근 파일은 그 트랜잭션이 끝날 때까지 휴지통으로 못 가고, 그 뒤 잠그면 비어 있다")
    void trashWaitsForTheCommitAndTheTrashedRowIsNoLongerLocked() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID id = tx.execute(status -> fileRepository.save(
                new FileJpaEntity(namespaceId, "a.bin", "/", UUID.randomUUID(), FileStatus.UPLOADED, false)).getId());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> commit = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            assertThat(adapter.lockActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "a.bin")).isPresent();
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> trash = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status ->
                entityManager.createNativeQuery("update file set status = 'TRASHED', trashed_at = now() where id = :id")
                        .setParameter("id", id).executeUpdate()));

        Thread.sleep(500);
        assertThat(trash).isNotDone();
        release.countDown();
        commit.get(10, TimeUnit.SECONDS);
        trash.get(10, TimeUnit.SECONDS);

        Optional<File> after = tx.execute(status ->
                adapter.lockActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "a.bin"));
        assertThat(after).isEmpty();
    }

    @Test
    @DisplayName("휴지통 이동이 진행 중이면 commit의 잠금은 기다렸다가, 끝난 뒤 다시 확인해 비어 있다")
    void lockWaitsForAnInFlightTrashAndThenFindsNothing() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID id = tx.execute(status -> fileRepository.save(
                new FileJpaEntity(namespaceId, "a.bin", "/", UUID.randomUUID(), FileStatus.UPLOADED, false)).getId());
        CountDownLatch trashed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> trash = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery("update file set status = 'TRASHED', trashed_at = now() where id = :id")
                    .setParameter("id", id).executeUpdate();
            trashed.countDown();
            await(release);
        }));
        assertThat(trashed.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Optional<File>> commit = CompletableFuture.supplyAsync(() -> tx.execute(status ->
                adapter.lockActiveByNamespaceIdAndPathAndName(new NamespaceId(namespaceId), "/", "a.bin")));

        Thread.sleep(500);
        assertThat(commit).isNotDone();
        release.countDown();
        trash.get(10, TimeUnit.SECONDS);

        assertThat(commit.get(10, TimeUnit.SECONDS)).isEmpty();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
