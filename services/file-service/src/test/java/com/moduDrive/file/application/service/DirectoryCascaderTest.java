package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.ReleaseBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class DirectoryCascaderTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private ReleaseBlocksPort releaseBlocksPort;
    @InjectMocks private DirectoryCascader directoryCascader;

    private final NamespaceId namespaceId = new NamespaceId(UUID.randomUUID());
    private final LocalDateTime trashedAt = LocalDateTime.now();
    private final UUID deletedBy = UUID.randomUUID();

    private File childAt(String path, String name, FileStatus status) {
        return childAt(path, name, status, trashedAt);
    }

    private File childAt(String path, String name, FileStatus status, LocalDateTime trashTime) {
        File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId.value()),
                new FileName(name), new FilePath(path),
                new FileOwnerId(UUID.randomUUID()), null, null, status, new FileIsDirectory(false));
        file.markUpdatedAt(trashTime);
        if (FileStatus.REMOVED.contains(status)) file.markTrashedAt(trashTime);
        return file;
    }

    // What each subtree rewrite does to the rows (skip removed, keep tombstones, ...) is SQL now —
    // covered on real Postgres in FilePersistenceAdapterTest.WhenRewritingASubtree.

    @Test
    @DisplayName("이동한 디렉토리의 하위 경로를 한 번에 바꾼다")
    void movesTheSubtreeInOneCall() {
        directoryCascader.movePath(namespaceId, "/A", "/C/A");

        then(saveFilePort).should().moveSubtree(namespaceId, "/A", "/C/A");
    }

    @Test
    @DisplayName("전체 경로가 바뀌지 않았다면 아무 것도 하지 않는다")
    void doesNothingWhenPrefixUnchanged() {
        directoryCascader.movePath(namespaceId, "/A", "/A");

        then(findFilePort).shouldHaveNoInteractions();
        then(saveFilePort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("휴지통으로 보낼 때 하위 전체를 디렉토리와 같은 시각으로 보낸다")
    void trashesTheSubtreeAtTheDirectorysInstant() {
        directoryCascader.softDelete(namespaceId, "/A", trashedAt);

        then(saveFilePort).should().trashSubtree(namespaceId, "/A", trashedAt);
    }

    @Test
    @DisplayName("복원할 때 하위 전체를 한 번에 복원한다")
    void restoresTheSubtreeInOneCall() {
        directoryCascader.restore(namespaceId, "/A");

        then(saveFilePort).should().restoreSubtree(namespaceId, "/A");
    }

    @Test
    @DisplayName("영구 삭제할 때 하위 전체의 블록을 한 번에 풀고 한 번에 tombstone으로 만든다")
    void purgeReleasesAndTombstonesTheSubtreeOnce() {
        File deletedDirectory = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId.value()),
                new FileName("하위폴더"), new FilePath("/A"),
                new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.TRASHED, new FileIsDirectory(true));
        deletedDirectory.markTrashedAt(trashedAt);
        File file = childAt("/A/하위폴더", "b.txt", FileStatus.TRASHED);
        given(findFilePort.lockTrashedByNamespaceIdAndPathStartingWith(namespaceId, "/A"))
                .willReturn(List.of(deletedDirectory, file));

        directoryCascader.purge(namespaceId, "/A", trashedAt, deletedBy);

        // Blocks first: purgeFiles deletes the versions that point at them.
        List<FileId> ids = List.of(new FileId(deletedDirectory.getId()), new FileId(file.getId()));
        InOrder order = inOrder(releaseBlocksPort, saveFilePort);
        order.verify(releaseBlocksPort).releaseBlocksOfFiles(List.of(deletedDirectory.getId(), file.getId()));
        order.verify(saveFilePort).purgeFiles(ids, deletedBy);
    }

    @Test
    @DisplayName("같은 경로를 나중에 재사용한, 아직 보존 기간이 남은 다른 디렉토리는 건드리지 않는다")
    void purgeSkipsADescendantTrashedLaterAtTheSamePath() {
        // The root being purged was trashed at `trashedAt`. A namesake directory at the same
        // path was created and trashed independently, later — its descendant must survive.
        File ownDescendant = childAt("/A", "b.txt", FileStatus.TRASHED, trashedAt);
        File unrelatedNamesakeDescendant = childAt("/A", "c.txt", FileStatus.TRASHED, trashedAt.plusDays(29));
        given(findFilePort.lockTrashedByNamespaceIdAndPathStartingWith(namespaceId, "/A"))
                .willReturn(List.of(ownDescendant, unrelatedNamesakeDescendant));

        directoryCascader.purge(namespaceId, "/A", trashedAt, deletedBy);

        then(releaseBlocksPort).should().releaseBlocksOfFiles(List.of(ownDescendant.getId()));
        then(saveFilePort).should().purgeFiles(List.of(new FileId(ownDescendant.getId())), deletedBy);
    }
}
