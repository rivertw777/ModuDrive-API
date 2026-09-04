package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.PurgeStorageBlocksPort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DirectoryCascaderTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private PurgeStorageBlocksPort purgeStorageBlocksPort;
    @InjectMocks private DirectoryCascader directoryCascader;

    private final NamespaceId namespaceId = new NamespaceId(UUID.randomUUID());
    private final LocalDateTime trashedAt = LocalDateTime.now();

    private File childAt(String path, String name) {
        return childAt(path, name, FileStatus.UPLOADED);
    }

    private File childAt(String path, String name, FileStatus status) {
        return childAt(path, name, status, trashedAt);
    }

    private File childAt(String path, String name, FileStatus status, LocalDateTime trashTime) {
        File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId.value()),
                new FileName(name), new FilePath(path),
                new FileOwnerId(UUID.randomUUID()), null, null, status, new FileIsDirectory(false));
        file.markUpdatedAt(trashTime);
        if (status == FileStatus.DELETED) file.markTrashedAt(trashTime);
        return file;
    }

    @Nested
    @DisplayName("이동한 디렉토리 하위에 항목이 있을 때")
    class WhenDescendantsExist {

        @Test
        void rewritesDirectChildAndNestedGrandchild() {
            // A moved from "/A" to "/C/A": B lives directly in A, B2 lives two levels deep in A/Sub
            given(findFilePort.findByNamespaceIdAndPathStartingWith(namespaceId, "/A"))
                    .willReturn(List.of(childAt("/A", "b.txt"), childAt("/A/Sub", "b2.txt")));
            given(saveFilePort.saveFile(any())).willAnswer(inv -> inv.getArgument(0));

            directoryCascader.movePath(namespaceId, "/A", "/C/A");

            ArgumentCaptor<File> saved = ArgumentCaptor.forClass(File.class);
            then(saveFilePort).should(times(2)).saveFile(saved.capture());
            assertThat(saved.getAllValues()).extracting(File::getPath)
                    .containsExactly("/C/A", "/C/A/Sub");
        }
    }

    @Test
    @DisplayName("전체 경로가 바뀌지 않았다면 아무 것도 하지 않는다")
    void doesNothingWhenPrefixUnchanged() {
        directoryCascader.movePath(namespaceId, "/A", "/A");

        then(findFilePort).shouldHaveNoInteractions();
        then(saveFilePort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("휴지통으로 보낼 때 이미 삭제된 하위 항목은 건드리지 않는다")
    void softDeleteSkipsAlreadyDeletedDescendant() {
        File active = childAt("/A", "b.txt", FileStatus.UPLOADED);
        File alreadyDeleted = childAt("/A", "c.txt", FileStatus.DELETED);
        given(findFilePort.findByNamespaceIdAndPathStartingWith(namespaceId, "/A"))
                .willReturn(List.of(active, alreadyDeleted));

        directoryCascader.softDelete(namespaceId, "/A", trashedAt);

        assertThat(active.getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(active.getTrashedAt()).isEqualTo(trashedAt);
        then(saveFilePort).should(times(1)).saveFile(active);
        then(saveFilePort).should(times(0)).saveFile(alreadyDeleted);
    }

    @Test
    @DisplayName("복원할 때 개별 purge된 tombstone 하위 항목은 되살리지 않는다")
    void restoreSkipsPurgedTombstoneDescendant() {
        File tombstone = childAt("/A", "b.txt", FileStatus.DELETED);
        tombstone.markDeletedAt(trashedAt.plusMinutes(5));
        given(findFilePort.findByNamespaceIdAndPathStartingWith(namespaceId, "/A"))
                .willReturn(List.of(tombstone));

        directoryCascader.restore(namespaceId, "/A");

        assertThat(tombstone.getStatus()).isEqualTo(FileStatus.DELETED);
        then(saveFilePort).should(times(0)).saveFile(tombstone);
    }

    @Test
    @DisplayName("복원할 때 삭제되지 않은 하위 항목은 건드리지 않는다")
    void restoreSkipsNonDeletedDescendant() {
        File deleted = childAt("/A", "b.txt", FileStatus.DELETED);
        File active = childAt("/A", "c.txt", FileStatus.UPLOADED);
        given(findFilePort.findByNamespaceIdAndPathStartingWith(namespaceId, "/A"))
                .willReturn(List.of(deleted, active));

        directoryCascader.restore(namespaceId, "/A");

        assertThat(deleted.getStatus()).isEqualTo(FileStatus.UPLOADED);
        then(saveFilePort).should(times(1)).saveFile(deleted);
        then(saveFilePort).should(times(0)).saveFile(active);
    }

    @Test
    @DisplayName("영구 삭제할 때 삭제되지 않은 하위 항목은 지우지 않는다")
    void purgeSkipsNonDeletedDescendant() {
        File deleted = childAt("/A", "b.txt", FileStatus.DELETED);
        File restoredEarly = childAt("/A", "c.txt", FileStatus.UPLOADED);
        given(findFilePort.findByNamespaceIdAndPathStartingWith(namespaceId, "/A"))
                .willReturn(List.of(deleted, restoredEarly));

        directoryCascader.purge(namespaceId, "/A", trashedAt);

        then(saveFilePort).should(times(1)).purgeFile(new FileId(deleted.getId()));
        then(saveFilePort).should(times(0)).purgeFile(new FileId(restoredEarly.getId()));
        then(purgeStorageBlocksPort).should(times(1))
                .purgeBlocks(new FileId(deleted.getId()), deleted.getOwnerId());
        then(purgeStorageBlocksPort).should(times(0)).purgeBlocks(new FileId(restoredEarly.getId()), restoredEarly.getOwnerId());
    }

    @Test
    @DisplayName("영구 삭제할 때 삭제된 하위 디렉토리는 자신의 블록을 지우지 않는다")
    void purgeSkipsBlockDeletionForADirectoryDescendant() {
        File deletedDirectory = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId.value()),
                new FileName("하위폴더"), new FilePath("/A"),
                new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.DELETED, new FileIsDirectory(true));
        deletedDirectory.markUpdatedAt(trashedAt);
        deletedDirectory.markTrashedAt(trashedAt);
        given(findFilePort.findByNamespaceIdAndPathStartingWith(namespaceId, "/A"))
                .willReturn(List.of(deletedDirectory));

        directoryCascader.purge(namespaceId, "/A", trashedAt);

        then(saveFilePort).should(times(1)).purgeFile(new FileId(deletedDirectory.getId()));
        then(purgeStorageBlocksPort).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("같은 경로를 나중에 재사용한, 아직 보존 기간이 남은 다른 디렉토리는 건드리지 않는다")
    void purgeSkipsADescendantTrashedLaterAtTheSamePath() {
        // The root being purged was trashed at `trashedAt`. A namesake directory at the same
        // path was created and trashed independently, later — its descendant must survive.
        File ownDescendant = childAt("/A", "b.txt", FileStatus.DELETED, trashedAt);
        File unrelatedNamesakeDescendant = childAt("/A", "c.txt", FileStatus.DELETED, trashedAt.plusDays(29));
        given(findFilePort.findByNamespaceIdAndPathStartingWith(namespaceId, "/A"))
                .willReturn(List.of(ownDescendant, unrelatedNamesakeDescendant));

        directoryCascader.purge(namespaceId, "/A", trashedAt);

        then(saveFilePort).should(times(1)).purgeFile(new FileId(ownDescendant.getId()));
        then(saveFilePort).should(times(0)).purgeFile(new FileId(unrelatedNamesakeDescendant.getId()));
        then(purgeStorageBlocksPort).should(times(0))
                .purgeBlocks(new FileId(unrelatedNamesakeDescendant.getId()), unrelatedNamesakeDescendant.getOwnerId());
    }
}
