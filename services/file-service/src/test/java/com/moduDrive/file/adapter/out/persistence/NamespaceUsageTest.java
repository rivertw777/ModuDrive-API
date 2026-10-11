package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.infrastructure.jpa.config.AuditingConfig;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceQuotaBytes;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The drive usage counter on real Postgres with real transactions — the slice's per-test rollback
 * transaction is off, so each step commits and the rows are cleaned up. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({NamespacePersistenceAdapter.class, FilePersistenceAdapter.class, FileMapper.class, AuditingConfig.class})
class NamespaceUsageTest {

    private static final long QUOTA = 10L * 1024 * 1024;
    private static final long MIB = 1024 * 1024;

    @Autowired private NamespacePersistenceAdapter namespaceAdapter;
    @Autowired private FilePersistenceAdapter fileAdapter;
    @Autowired private SpringDataFileRepository fileRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private NamespaceId namespaceId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        Namespace saved = tx.execute(status -> namespaceAdapter.saveNamespace(
                Namespace.create(new NamespaceUserId(UUID.randomUUID()), new NamespaceQuotaBytes(QUOTA))));
        namespaceId = new NamespaceId(saved.getId());
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            entityManager.createNativeQuery("delete from file where namespace_id = :id")
                    .setParameter("id", namespaceId.value()).executeUpdate();
            entityManager.createNativeQuery("delete from namespace where id = :id")
                    .setParameter("id", namespaceId.value()).executeUpdate();
        });
    }

    private long used() {
        return ((Number) entityManager.createNativeQuery("select used_bytes from namespace where id = :id")
                .setParameter("id", namespaceId.value()).getSingleResult()).longValue();
    }

    private boolean add(long delta) {
        return Boolean.TRUE.equals(tx.execute(status -> namespaceAdapter.addUsedBytes(namespaceId, delta)));
    }

    @Nested
    @DisplayName("사용량을 더할 때")
    class WhenAdding {

        @Test
        @DisplayName("한도 안이면 더하고, 넘으면 아무것도 바꾸지 않고 false다")
        void addsOnlyWithinTheQuota() {
            assertThat(add(QUOTA - 1)).isTrue();
            assertThat(add(2)).isFalse();
            assertThat(add(1)).isTrue();

            assertThat(used()).isEqualTo(QUOTA);
        }

        @Test
        @DisplayName("줄이는 갱신은 한도를 넘은 드라이브에서도 적용된다")
        void shrinkingAlwaysApplies() {
            tx.executeWithoutResult(status -> entityManager.createNativeQuery(
                    "update namespace set used_bytes = :over where id = :id")
                    .setParameter("over", QUOTA + 5).setParameter("id", namespaceId.value()).executeUpdate());

            assertThat(add(-3)).isTrue();
            assertThat(add(0)).isTrue();

            assertThat(used()).isEqualTo(QUOTA + 2);
        }

        @Test
        @DisplayName("동시에 20개가 1MiB씩 더해도 10MiB 한도에서 정확히 10개만 성공한다")
        void concurrentAddsNeverPassTheQuota() throws Exception {
            // A thread each, so all 20 really are in flight at once (the common pool has cores - 1).
            ExecutorService pool = Executors.newFixedThreadPool(20);
            CyclicBarrier start = new CyclicBarrier(20);
            List<CompletableFuture<Boolean>> adds = IntStream.range(0, 20)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        await(start);
                        return add(MIB);
                    }, pool))
                    .toList();

            long succeeded = 0;
            for (CompletableFuture<Boolean> f : adds) {
                if (f.get(30, TimeUnit.SECONDS)) {
                    succeeded++;
                }
            }

            pool.shutdown();

            assertThat(succeeded).isEqualTo(10);
            assertThat(used()).isEqualTo(QUOTA);
        }
    }

    @Nested
    @DisplayName("파일을 영구 삭제할 때")
    class WhenPurging {

        private UUID trashedFile(boolean directory, Long size) {
            return tx.execute(status -> {
                UUID id = fileRepository.save(new FileJpaEntity(namespaceId.value(), "a" + UUID.randomUUID(), "/",
                        UUID.randomUUID(), FileStatus.TRASHED, directory)).getId();
                entityManager.flush();
                entityManager.createNativeQuery("update file set file_size = :size where id = :id")
                        .setParameter("size", size).setParameter("id", id).executeUpdate();
                return id;
            });
        }

        private void purge(UUID fileId) {
            tx.executeWithoutResult(status -> fileAdapter.purgeFile(new FileId(fileId), null));
        }

        @Test
        @DisplayName("그 파일 크기만큼 사용량을 줄이고, 두 번째 purge는 아무것도 줄이지 않는다")
        void freesTheFileSizeOnce() {
            add(300);
            UUID file = trashedFile(false, 100L);

            purge(file);
            purge(file);

            assertThat(used()).isEqualTo(200);
        }

        @Test
        @DisplayName("폴더나 크기가 없는 파일은 사용량을 바꾸지 않는다")
        void freesNothingForAFolderOrASizelessRow() {
            add(300);

            purge(trashedFile(true, 0L));
            purge(trashedFile(false, null));

            assertThat(used()).isEqualTo(300);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
