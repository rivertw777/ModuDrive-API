package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.file.application.port.out.ClaimUnreferencedBlocksPort.UnreferencedBlock;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs on real Postgres — the upsert and the release's CASE are native SQL, so this is where they
 * get checked. */
@DataJpaTest
@Import(BlockPersistenceAdapter.class)
class BlockPersistenceAdapterTest {

    @Autowired private BlockPersistenceAdapter adapter;
    @Autowired private SpringDataBlockRepository blockRepository;
    @Autowired private EntityManager entityManager;

    private final UUID owner = UUID.randomUUID();

    private Optional<BlockJpaEntity> row(String hash) {
        entityManager.flush();
        entityManager.clear();
        return blockRepository.findById(new BlockJpaEntity.Key(owner, hash));
    }

    private FileVersion versionOf(List<String> hashes) {
        return FileVersion.withId(new FileVersionId(UUID.randomUUID()), new FileVersionFileId(UUID.randomUUID()),
                new FileVersionOwnerId(owner), new FileVersionFileSize(1L),
                new FileVersionUploadId(UUID.randomUUID()), new FileVersionHashes(hashes));
    }

    @Nested
    @DisplayName("블록을 참조할 때")
    class WhenReferencing {

        @Test
        @DisplayName("처음 커밋된 블록은 행을 만들고, 이미 있으면 참조 수를 더한다")
        void createsOrIncrementsRows() {
            adapter.referenceBlocks(owner, Map.of("h1", 4), Map.of("h1", 2));
            adapter.referenceBlocks(owner, Map.of("h1", 4), Map.of("h1", 1));

            assertThat(row("h1")).get().satisfies(b -> {
                assertThat(b.getRefCount()).isEqualTo(3);
                assertThat(b.getSize()).isEqualTo(4);
            });
        }

        @Test
        @DisplayName("참조가 끊겼던 블록을 다시 참조하면 유예 시각을 지운다")
        void clearsTheGraceStartOnReuse() {
            adapter.referenceBlocks(owner, Map.of("h1", 4), Map.of("h1", 1));
            adapter.releaseBlocks(List.of(versionOf(List.of("h1"))));
            assertThat(row("h1")).get().extracting(BlockJpaEntity::getUnreferencedAt).isNotNull();

            adapter.referenceBlocks(owner, Map.of("h1", 4), Map.of("h1", 1));

            assertThat(row("h1")).get().satisfies(b -> {
                assertThat(b.getRefCount()).isEqualTo(1);
                assertThat(b.getUnreferencedAt()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("버전의 참조를 풀 때")
    class WhenReleasing {

        @Test
        @DisplayName("한 버전에 여러 번 나온 블록은 그만큼 빼고, 0이 된 블록만 유예를 시작한다")
        void decrementsAndStartsGraceOnlyAtZero() {
            adapter.referenceBlocks(owner, Map.of("h1", 4, "h2", 4), Map.of("h1", 2, "h2", 2));

            adapter.releaseBlocks(List.of(versionOf(List.of("h1", "h1", "h2"))));

            assertThat(row("h1")).get().satisfies(b -> {
                assertThat(b.getRefCount()).isZero();
                assertThat(b.getUnreferencedAt()).isNotNull();
            });
            assertThat(row("h2")).get().satisfies(b -> {
                assertThat(b.getRefCount()).isEqualTo(1);
                assertThat(b.getUnreferencedAt()).isNull();
            });
        }
    }

    @Nested
    @DisplayName("커밋된 블록을 찾을 때")
    class WhenFinding {

        @Test
        @DisplayName("이 소유자의 행이 있는 해시만 크기와 함께 돌려준다")
        void returnsOnlyTheOwnersRows() {
            adapter.referenceBlocks(owner, Map.of("h1", 4), Map.of("h1", 1));
            adapter.referenceBlocks(UUID.randomUUID(), Map.of("h2", 4), Map.of("h2", 1));

            assertThat(adapter.lockCommittedBlocks(owner, List.of("h1", "h2"))).containsExactly(Map.entry("h1", 4));
            assertThat(adapter.findCommittedHashes(owner, List.of("h1", "h2"))).containsExactly("h1");
            assertThat(adapter.lockCommittedBlocks(owner, List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("참조 없는 블록을 거둘 때")
    class WhenClaiming {

        @Test
        @DisplayName("기준 시각 전에 0이 된 블록만 행을 지우고 돌려준다")
        void claimsOnlyRowsUnreferencedBeforeTheCutoff() {
            adapter.referenceBlocks(owner, Map.of("old", 4, "live", 4), Map.of("old", 1, "live", 1));
            adapter.releaseBlocks(List.of(versionOf(List.of("old"))));

            assertThat(adapter.claimUnreferenced(LocalDateTime.now().minusHours(1), 10)).isEmpty();

            List<UnreferencedBlock> claimed = adapter.claimUnreferenced(LocalDateTime.now().plusMinutes(1), 10);

            assertThat(claimed).containsExactly(new UnreferencedBlock(owner, "old"));
            assertThat(row("old")).isEmpty();
            assertThat(row("live")).isPresent();
        }
    }
}
