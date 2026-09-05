package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ListSharedDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ListSharedDirectoryServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private FileFavoritePort fileFavoritePort;
    @Mock private FindMemberByIdPort findMemberByIdPort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private ListSharedDirectoryService listSharedDirectoryService;

    @BeforeEach
    void defaults() {
        lenient().when(fileFavoritePort.favoriteFileIds(any())).thenReturn(Set.of());
        lenient().when(fileAccessGuard.effectiveRole(any(), any())).thenReturn(null);
        lenient().when(fileAccessGuard.resolveGrant(any(), any())).thenReturn(Optional.empty());
        lenient().when(findMemberByIdPort.findMemberById(any())).thenReturn(new MemberSummary("홍길동", "owner@modudrive.com"));
        lenient().when(findFileSharePort.existsByFileIdAndSharedWithUserId(any(), any())).thenReturn(false);
    }

    private final UUID dirId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID namespaceId = UUID.randomUUID();
    private final ListSharedDirectoryCommand command = new ListSharedDirectoryCommand(dirId, callerId);

    private File entry(String name, String path, boolean directory, FileStatus status) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(namespaceId),
                new FileName(name), new FilePath(path), new FileOwnerId(UUID.randomUUID()),
                null, null, status, new FileIsDirectory(directory));
    }

    private File dir() {
        return File.withId(new FileId(dirId), new FileNamespaceId(namespaceId),
                new FileName("shared"), new FilePath("/"), new FileOwnerId(UUID.randomUUID()),
                null, null, FileStatus.UPLOADED, new FileIsDirectory(true));
    }

    @Nested
    @DisplayName("접근 가능한 디렉토리를 조회할 때")
    class WhenAccessible {

        @Test
        void returnsNonDeletedChildren() {
            File child = entry("a.txt", "/shared", false, FileStatus.UPLOADED);
            File trashed = entry("b.txt", "/shared", false, FileStatus.DELETED);
            given(findFilePort.findById(command.getDirectoryId())).willReturn(Optional.of(dir()));
            given(findFilePort.findByNamespaceIdAndPath(new NamespaceId(namespaceId), "/shared"))
                    .willReturn(List.of(child, trashed));

            List<FileView> result = listSharedDirectoryService.listSharedDirectory(command);

            assertThat(result).extracting(FileView::file).containsExactly(child);
        }

        @Test
        @DisplayName("자식 파일도 폴더 자체의 공유 정보(공유한 사용자/공유된 날짜)를 그대로 물려받는다")
        void childrenInheritTheDirectorysShareAttribution() {
            File child = entry("a.txt", "/shared", false, FileStatus.UPLOADED);
            LocalDateTime sharedAt = LocalDateTime.of(2026, 9, 4, 14, 10);
            FileShare grant = FileShare.withId(new FileShare.FileShareId(UUID.randomUUID()),
                    new FileShare.FileShareFileId(dirId), new FileShare.FileShareOwnerId(UUID.randomUUID()),
                    new FileShare.FileShareSharedWithUserId(callerId), new FileShare.FileShareRole(Role.VIEWER),
                    sharedAt);
            given(findFilePort.findById(command.getDirectoryId())).willReturn(Optional.of(dir()));
            given(findFilePort.findByNamespaceIdAndPath(new NamespaceId(namespaceId), "/shared"))
                    .willReturn(List.of(child));
            given(fileAccessGuard.resolveGrant(any(), eq(callerId))).willReturn(Optional.of(grant));
            given(fileAccessGuard.effectiveRole(any(), eq(callerId))).willReturn(Role.VIEWER);

            List<FileView> result = listSharedDirectoryService.listSharedDirectory(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).sharedByName()).isEqualTo("홍길동");
            assertThat(result.get(0).sharedAt()).isEqualTo(sharedAt);
            assertThat(result.get(0).callerRole()).isEqualTo(Role.VIEWER);
        }

        @Test
        @DisplayName("자식이 호출자에게 따로 직접 공유돼 있으면 목록에서 빠진다 (구글 드라이브와 동일 — 그건 최상위 자기 항목으로 따로 뜬다)")
        void excludesAChildTheCallerAlreadyHasADirectShareOn() {
            File plainChild = entry("b.txt", "/shared", false, FileStatus.UPLOADED);
            File individuallySharedChild = entry("a.txt", "/shared", false, FileStatus.UPLOADED);
            given(findFilePort.findById(command.getDirectoryId())).willReturn(Optional.of(dir()));
            given(findFilePort.findByNamespaceIdAndPath(new NamespaceId(namespaceId), "/shared"))
                    .willReturn(List.of(plainChild, individuallySharedChild));
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(
                    new FileId(individuallySharedChild.getId()), callerId)).willReturn(true);

            List<FileView> result = listSharedDirectoryService.listSharedDirectory(command);

            assertThat(result).extracting(FileView::file).containsExactly(plainChild);
        }
    }

    @Nested
    @DisplayName("대상이 디렉토리가 아닐 때")
    class WhenNotADirectory {

        @Test
        void throwsDirectoryNotFound() {
            given(findFilePort.findById(command.getDirectoryId()))
                    .willReturn(Optional.of(entry("report.pdf", "/", false, FileStatus.UPLOADED)));

            Throwable thrown = catchThrowable(() -> listSharedDirectoryService.listSharedDirectory(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.DIRECTORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("호출자에게 접근 권한이 없을 때")
    class WhenAccessDenied {

        @Test
        void propagatesAccessDenied() {
            given(findFilePort.findById(command.getDirectoryId())).willReturn(Optional.of(dir()));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requirePermission(any(File.class), eq(callerId), eq(Permission.READ));

            Throwable thrown = catchThrowable(() -> listSharedDirectoryService.listSharedDirectory(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("디렉토리가 없을 때")
    class WhenNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getDirectoryId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> listSharedDirectoryService.listSharedDirectory(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }
}
