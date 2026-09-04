package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.EmptyTrashCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.*;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class EmptyTrashServiceTest {

    @Mock private FindNamespacePort findNamespacePort;
    @Mock private FindFilePort findFilePort;
    @Mock private FilePurger filePurger;
    @InjectMocks private EmptyTrashService emptyTrashService;

    private static final long TEST_QUOTA_BYTES = 21474836480L;

    private final UUID userId = UUID.randomUUID();
    private final EmptyTrashCommand command = new EmptyTrashCommand(userId);
    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()), new NamespaceUserId(userId), new NamespaceRootPath("/1"), new NamespaceQuotaBytes(TEST_QUOTA_BYTES));

    @Nested
    @DisplayName("휴지통에 파일과 디렉토리가 섞여 있을 때")
    class WhenTrashHasFilesAndDirectories {

        @Test
        void purgesOnlyTheRoots() {
            File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("report.pdf"), new FilePath("/1"),
                    new FileOwnerId(userId), null, null, FileStatus.DELETED, new FileIsDirectory(false));
            File directory = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("폴더"), new FilePath("/1"),
                    new FileOwnerId(userId), null, null, FileStatus.DELETED, new FileIsDirectory(true));
            File nestedFile = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("b.txt"), new FilePath("/1/폴더"),
                    new FileOwnerId(userId), null, null, FileStatus.DELETED, new FileIsDirectory(false));

            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findTrashedNotPurged(any()))
                    .willReturn(List.of(file, directory, nestedFile));

            emptyTrashService.emptyTrash(command);

            then(filePurger).should().purgeRoot(file);
            then(filePurger).should().purgeRoot(directory);
            // nestedFile is under the purged directory, not a root — DirectoryCascader.purge
            // (inside FilePurger) handles it, EmptyTrashService never touches it directly.
            then(filePurger).should(Mockito.never()).purgeRoot(nestedFile);
        }
    }

    @Nested
    @DisplayName("루트 하나의 purge가 실패할 때")
    class WhenOneRootFailsToPurge {

        @Test
        void stillPurgesTheRemainingRoots() {
            File failing = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("broken.pdf"), new FilePath("/1"),
                    new FileOwnerId(userId), null, null, FileStatus.DELETED, new FileIsDirectory(false));
            File ok = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                    new FileName("fine.pdf"), new FilePath("/1"),
                    new FileOwnerId(userId), null, null, FileStatus.DELETED, new FileIsDirectory(false));

            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findTrashedNotPurged(any()))
                    .willReturn(List.of(failing, ok));
            willThrow(new RuntimeException("storage-service unreachable")).given(filePurger).purgeRoot(failing);

            emptyTrashService.emptyTrash(command);

            then(filePurger).should().purgeRoot(ok);
        }
    }

    @Nested
    @DisplayName("휴지통이 비어 있을 때")
    class WhenTrashIsEmpty {

        @Test
        void doesNothing() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findTrashedNotPurged(any())).willReturn(List.of());

            emptyTrashService.emptyTrash(command);

            then(filePurger).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("네임스페이스가 없을 때")
    class WhenNamespaceNotFound {

        @Test
        void throwsNamespaceNotFound() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> emptyTrashService.emptyTrash(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.NAMESPACE_NOT_FOUND);
            then(filePurger).shouldHaveNoInteractions();
        }
    }
}
