package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.DeleteFileVersionsPort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.application.port.out.LockCommittedBlocksPort;
import com.moduDrive.file.application.port.out.ReleaseBlocksPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.fixture.FileVersionTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import static com.moduDrive.file.fixture.FileVersionTestFixture.HASH_A;
import static com.moduDrive.file.fixture.FileVersionTestFixture.HASH_B;
import static com.moduDrive.file.fixture.FileVersionTestFixture.OWNER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class FileVersionPrunerTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileVersionsPort findFileVersionsPort;
    @Mock private LockCommittedBlocksPort lockCommittedBlocksPort;
    @Mock private ReleaseBlocksPort releaseBlocksPort;
    @Mock private DeleteFileVersionsPort deleteFileVersionsPort;
    @InjectMocks private FileVersionPruner fileVersionPruner;

    private final FileId fileId = new FileId(UUID.randomUUID());
    private final LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
    private final FileVersion old = FileVersionTestFixture.aVersion(UUID.randomUUID(), fileId.value(), 10L, List.of(HASH_B, HASH_A));

    private void fileExists() {
        given(findFilePort.lockById(fileId)).willReturn(Optional.of(File.withId(fileId,
                new FileNamespaceId(UUID.randomUUID()), new FileName("report.pdf"), new FilePath("/"),
                new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED, new FileIsDirectory(false))));
    }

    @Nested
    @DisplayName("보존 기간이 지난 버전이 있을 때")
    class WhenVersionsArePastRetention {

        @Test
        @DisplayName("commit과 같은 순서(블록 → 파일 행)로 잠그고, 다시 읽은 뒤 블록 참조를 풀고 버전 행을 지운다")
        void locksBlocksThenTheFileThenReleasesAndDeletes() {
            given(findFileVersionsPort.findVersionsToPrune(fileId, cutoff, 100)).willReturn(List.of(old));
            fileExists();

            int pruned = fileVersionPruner.prune(fileId, cutoff, 100);

            assertThat(pruned).isEqualTo(1);
            InOrder order = inOrder(findFileVersionsPort, lockCommittedBlocksPort, findFilePort,
                    releaseBlocksPort, deleteFileVersionsPort);
            order.verify(findFileVersionsPort).findVersionsToPrune(fileId, cutoff, 100);
            // Hash order, as a commit locks them.
            order.verify(lockCommittedBlocksPort).lockCommittedBlocks(OWNER_ID, new TreeSet<>(List.of(HASH_A, HASH_B)));
            order.verify(findFilePort).lockById(fileId);
            order.verify(findFileVersionsPort).findVersionsToPrune(fileId, cutoff, 100);
            order.verify(releaseBlocksPort).releaseBlocks(List.of(old));
            order.verify(deleteFileVersionsPort).deleteFileVersions(List.of(old));
        }
    }

    @Nested
    @DisplayName("잠그고 다시 읽은 대상이 처음과 다를 때")
    class WhenTheSecondReadDiffers {

        @Test
        @DisplayName("두 번 다 고른 버전만 지운다 — 새로 고른 버전은 블록을 잠그지 않았으니 다음 실행으로 미룬다")
        void dropsOnlyVersionsPickedByBothReads() {
            FileVersion pickedOnlyNow = FileVersionTestFixture.aVersion(UUID.randomUUID(), fileId.value(), 10L);
            given(findFileVersionsPort.findVersionsToPrune(fileId, cutoff, 100))
                    .willReturn(List.of(old))
                    .willReturn(List.of(old, pickedOnlyNow));
            fileExists();

            assertThat(fileVersionPruner.prune(fileId, cutoff, 100)).isEqualTo(1);

            then(releaseBlocksPort).should().releaseBlocks(List.of(old));
            then(deleteFileVersionsPort).should().deleteFileVersions(List.of(old));
        }

        @Test
        @DisplayName("그사이 사라졌으면 아무것도 풀지 않는다")
        void releasesNothingWhenTheVersionIsGone() {
            given(findFileVersionsPort.findVersionsToPrune(fileId, cutoff, 100))
                    .willReturn(List.of(old))
                    .willReturn(List.of());
            fileExists();

            assertThat(fileVersionPruner.prune(fileId, cutoff, 100)).isZero();

            then(releaseBlocksPort).shouldHaveNoInteractions();
            then(deleteFileVersionsPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("지울 버전이 없을 때")
    class WhenNothingIsPastRetention {

        @Test
        @DisplayName("아무것도 잠그지 않는다")
        void locksNothing() {
            given(findFileVersionsPort.findVersionsToPrune(fileId, cutoff, 100)).willReturn(List.of());

            assertThat(fileVersionPruner.prune(fileId, cutoff, 100)).isZero();

            then(lockCommittedBlocksPort).shouldHaveNoInteractions();
            then(findFilePort).shouldHaveNoInteractions();
            then(releaseBlocksPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일 행이 없을 때")
    class WhenTheFileIsGone {

        @Test
        @DisplayName("블록 참조를 풀지 않는다")
        void releasesNothing() {
            given(findFileVersionsPort.findVersionsToPrune(fileId, cutoff, 100)).willReturn(List.of(old));
            given(findFilePort.lockById(fileId)).willReturn(Optional.empty());

            assertThat(fileVersionPruner.prune(fileId, cutoff, 100)).isZero();

            then(releaseBlocksPort).shouldHaveNoInteractions();
            then(deleteFileVersionsPort).shouldHaveNoInteractions();
        }
    }
}
