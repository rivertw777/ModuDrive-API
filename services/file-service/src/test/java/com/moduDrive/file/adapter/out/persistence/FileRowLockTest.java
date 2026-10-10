package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.config.AuditingConfig;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.File.FileName;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
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

    @Test
    @DisplayName("이름을 바꾸는 저장은 그사이 commit이 바꾼 현재 버전을 되돌리지 않는다")
    void aSaveWritesOnlyTheColumnsItChanged() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID id = tx.execute(status -> fileRepository.save(
                new FileJpaEntity(namespaceId, "a.bin", "/", UUID.randomUUID(), FileStatus.UPLOADED, false)).getId());
        UUID committedVersion = UUID.randomUUID();

        tx.executeWithoutResult(status -> {
            File read = adapter.findById(new FileId(id)).orElseThrow();
            // A commit lands between this read and the save below.
            TransactionTemplate commit = new TransactionTemplate(transactionManager);
            commit.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            commit.executeWithoutResult(inner ->
                    entityManager.createNativeQuery("update file set current_version_id = :version where id = :id")
                            .setParameter("version", committedVersion).setParameter("id", id).executeUpdate());
            read.rename(new FileName("b.bin"));
            adapter.saveFile(read);
        });

        FileJpaEntity after = tx.execute(status -> fileRepository.findById(id).orElseThrow());
        assertThat(after.getName()).isEqualTo("b.bin");
        assertThat(after.getCurrentVersionId()).isEqualTo(committedVersion);
    }

    @Test
    @DisplayName("영구 삭제의 하위 잠금은 휴지통에 있는 행만 잠그고 돌려준다")
    void thePurgeLocksOnlyTheTrashedRowsOfTheSubtree() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            fileRepository.save(new FileJpaEntity(namespaceId, "old.bin", "/D", UUID.randomUUID(), FileStatus.TRASHED, false));
            fileRepository.save(new FileJpaEntity(namespaceId, "live.bin", "/D", UUID.randomUUID(), FileStatus.UPLOADED, false));
            fileRepository.save(new FileJpaEntity(namespaceId, "x.bin", "/D2", UUID.randomUUID(), FileStatus.TRASHED, false));
        });

        List<File> locked = tx.execute(status ->
                adapter.lockTrashedByNamespaceIdAndPathStartingWith(new NamespaceId(namespaceId), "/D"));

        assertThat(locked).extracting(File::getName).containsExactly("old.bin");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
