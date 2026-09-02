package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class PurgeExpiredTrashServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FilePurger filePurger;
    @InjectMocks private PurgeExpiredTrashService purgeExpiredTrashService;

    @Nested
    @DisplayName("30일 넘게 휴지통에 있는 파일이 여러 네임스페이스에 걸쳐 있을 때")
    class WhenExpiredFilesSpanNamespaces {

        @Test
        void purgesOnlyTheRootsPerNamespace() {
            UUID namespaceA = UUID.randomUUID();
            UUID namespaceB = UUID.randomUUID();

            File fileInA = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceA),
                    new FileName("old.pdf"), new FilePath("/1"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.DELETED, new FileIsDirectory(false));
            File directoryInA = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceA),
                    new FileName("폴더"), new FilePath("/1"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.DELETED, new FileIsDirectory(true));
            File nestedFileInA = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceA),
                    new FileName("b.txt"), new FilePath("/1/폴더"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.DELETED, new FileIsDirectory(false));
            File fileInB = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceB),
                    new FileName("stale.png"), new FilePath("/2"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.DELETED, new FileIsDirectory(false));

            given(findFilePort.findByStatusAndUpdatedAtBefore(eq(FileStatus.DELETED), any(LocalDateTime.class)))
                    .willReturn(List.of(fileInA, directoryInA, nestedFileInA, fileInB));

            purgeExpiredTrashService.purgeExpiredTrash();

            then(filePurger).should().purgeRoot(fileInA);
            then(filePurger).should().purgeRoot(directoryInA);
            then(filePurger).should().purgeRoot(fileInB);
            then(filePurger).should(Mockito.never()).purgeRoot(nestedFileInA);
        }
    }

    @Nested
    @DisplayName("한 네임스페이스의 루트 purge가 실패할 때")
    class WhenOneRootFailsToPurge {

        @Test
        void stillPurgesRootsInOtherNamespaces() {
            UUID namespaceA = UUID.randomUUID();
            UUID namespaceB = UUID.randomUUID();
            File failing = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceA),
                    new FileName("broken.pdf"), new FilePath("/1"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.DELETED, new FileIsDirectory(false));
            File ok = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceB),
                    new FileName("fine.pdf"), new FilePath("/2"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.DELETED, new FileIsDirectory(false));

            given(findFilePort.findByStatusAndUpdatedAtBefore(eq(FileStatus.DELETED), any(LocalDateTime.class)))
                    .willReturn(List.of(failing, ok));
            willThrow(new RuntimeException("storage-service unreachable")).given(filePurger).purgeRoot(failing);

            purgeExpiredTrashService.purgeExpiredTrash();

            then(filePurger).should().purgeRoot(ok);
        }
    }

    @Nested
    @DisplayName("30일 넘게 휴지통에 있는 파일이 없을 때")
    class WhenNothingExpired {

        @Test
        void doesNothing() {
            given(findFilePort.findByStatusAndUpdatedAtBefore(eq(FileStatus.DELETED), any(LocalDateTime.class)))
                    .willReturn(List.of());

            purgeExpiredTrashService.purgeExpiredTrash();

            then(filePurger).shouldHaveNoInteractions();
        }
    }
}
