package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.in.command.ListRecentFilesCommand;
import com.moduDrive.file.application.port.out.FindFileAccessPort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileAccess;
import com.moduDrive.file.domain.model.FileAccess.FileAccessFileId;
import com.moduDrive.file.domain.model.FileAccess.FileAccessUserId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceQuotaBytes;
import com.moduDrive.file.domain.model.Namespace.NamespaceRootPath;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ListRecentFilesServiceTest {

    @Mock private FindFileAccessPort findFileAccessPort;
    @Mock private FindFilePort findFilePort;
    @Mock private FindNamespacePort findNamespacePort;
    @Mock private FindFileSharePort findFileSharePort;
    @InjectMocks private ListRecentFilesService listRecentFilesService;

    private final UUID userId = UUID.randomUUID();
    private final UUID ownNamespaceId = UUID.randomUUID();
    private final ListRecentFilesCommand command = new ListRecentFilesCommand(userId, 20);

    private File makeFile(UUID fileId, UUID namespaceId, FileStatus status) {
        return File.withId(
                new FileId(fileId), new FileNamespaceId(namespaceId),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(UUID.randomUUID()), null, null, status, new FileIsDirectory(false));
    }

    private FileAccess makeAccess(UUID fileId) {
        return FileAccess.of(new FileAccessUserId(userId), new FileAccessFileId(fileId), LocalDateTime.now());
    }

    private void givenOwnNamespace() {
        given(findNamespacePort.findByUserId(new NamespaceUserId(userId))).willReturn(Optional.of(
                Namespace.withId(new Namespace.NamespaceId(ownNamespaceId), new NamespaceUserId(userId),
                        new NamespaceRootPath("/" + userId), new NamespaceQuotaBytes(1_000_000L))));
    }

    @Nested
    @DisplayName("최근 접근한 파일이 있을 때")
    class WhenRecentAccessExists {

        @Test
        void returnsFilesInAccessOrderExcludingDeleted() {
            UUID activeFileId = UUID.randomUUID();
            UUID deletedFileId = UUID.randomUUID();
            givenOwnNamespace();

            given(findFileAccessPort.findByUserIdOrderByAccessedAtDesc(userId, 20))
                    .willReturn(List.of(makeAccess(activeFileId), makeAccess(deletedFileId)));
            given(findFilePort.findById(new FileId(activeFileId)))
                    .willReturn(Optional.of(makeFile(activeFileId, ownNamespaceId, FileStatus.UPLOADED)));
            given(findFilePort.findById(new FileId(deletedFileId)))
                    .willReturn(Optional.of(makeFile(deletedFileId, ownNamespaceId, FileStatus.DELETED)));

            List<File> result = listRecentFilesService.listRecentFiles(command);

            assertThat(result).extracting(File::getId).containsExactly(activeFileId);
        }
    }

    @Nested
    @DisplayName("최근 접근한 파일이 없을 때")
    class WhenNoRecentAccess {

        @Test
        void returnsEmptyList() {
            givenOwnNamespace();
            given(findFileAccessPort.findByUserIdOrderByAccessedAtDesc(userId, 20)).willReturn(List.of());

            List<File> result = listRecentFilesService.listRecentFiles(command);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("더 이상 접근 권한이 없는 파일일 때")
    class WhenNoLongerAccessible {

        @Test
        void excludesFileNotOwnedAndNotShared() {
            UUID otherUsersFileId = UUID.randomUUID();
            UUID otherNamespaceId = UUID.randomUUID();
            givenOwnNamespace();

            given(findFileAccessPort.findByUserIdOrderByAccessedAtDesc(userId, 20))
                    .willReturn(List.of(makeAccess(otherUsersFileId)));
            given(findFilePort.findById(new FileId(otherUsersFileId)))
                    .willReturn(Optional.of(makeFile(otherUsersFileId, otherNamespaceId, FileStatus.UPLOADED)));
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(new FileId(otherUsersFileId), userId))
                    .willReturn(false);

            List<File> result = listRecentFilesService.listRecentFiles(command);

            assertThat(result).isEmpty();
        }

        @Test
        void includesFileStillSharedWithUser() {
            UUID sharedFileId = UUID.randomUUID();
            UUID otherNamespaceId = UUID.randomUUID();
            givenOwnNamespace();

            given(findFileAccessPort.findByUserIdOrderByAccessedAtDesc(userId, 20))
                    .willReturn(List.of(makeAccess(sharedFileId)));
            given(findFilePort.findById(new FileId(sharedFileId)))
                    .willReturn(Optional.of(makeFile(sharedFileId, otherNamespaceId, FileStatus.UPLOADED)));
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(new FileId(sharedFileId), userId))
                    .willReturn(true);

            List<File> result = listRecentFilesService.listRecentFiles(command);

            assertThat(result).extracting(File::getId).containsExactly(sharedFileId);
        }
    }
}
