package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetFileCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Permission;
import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
class GetFileServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindMemberByIdPort findMemberByIdPort;
    @Mock private FileFavoritePort fileFavoritePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private GetFileService getFileService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID otherOwnerId = UUID.randomUUID();
    private final GetFileCommand command = new GetFileCommand(fileId, callerId);

    private File fileOwnedBy(UUID ownerId, FileStatus status) {
        return File.withId(
                new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(ownerId), null, null, status, new FileIsDirectory(false));
    }

    private FileShare grantTo(UUID sharedWithUserId, LocalDateTime sharedAt) {
        return FileShare.withId(new FileShareId(UUID.randomUUID()),
                new FileShareFileId(UUID.randomUUID()), new FileShareOwnerId(otherOwnerId),
                new FileShareSharedWithUserId(sharedWithUserId), new FileShareRole(Role.VIEWER), sharedAt);
    }

    @Nested
    @DisplayName("소유한 파일을 조회할 때")
    class WhenOwnFile {

        @Test
        void returnsTheFileWithNoRole() {
            given(findFilePort.findById(command.getFileId()))
                    .willReturn(Optional.of(fileOwnedBy(callerId, FileStatus.UPLOADED)));

            FileView result = getFileService.getFile(command);

            assertThat(result.file().getId()).isEqualTo(fileId);
            assertThat(result.callerRole()).isNull();
        }
    }

    @Nested
    @DisplayName("공유받은 파일을 조회할 때")
    class WhenSharedFile {

        @Test
        void returnsTheFileWithTheCallersRoleSharerAndPerUserFavorite() {
            given(findFilePort.findById(command.getFileId()))
                    .willReturn(Optional.of(fileOwnedBy(otherOwnerId, FileStatus.UPLOADED)));
            given(fileFavoritePort.isFavorite(callerId, fileId)).willReturn(true);
            given(fileAccessGuard.requirePermission(any(File.class), eq(callerId), eq(Permission.READ)))
                    .willReturn(new FileAccessGuard.Access(Role.EDITOR, grantTo(callerId, LocalDateTime.of(2026, 9, 4, 15, 5))));
            given(findMemberByIdPort.findMemberByIdOrUnknown(otherOwnerId))
                    .willReturn(new MemberSummary("홍길동", "owner@modudrive.com"));

            FileView result = getFileService.getFile(command);

            assertThat(result.callerRole()).isEqualTo(Role.EDITOR);
            assertThat(result.sharedByName()).isEqualTo("홍길동");
            assertThat(result.sharedByEmail()).isEqualTo("owner@modudrive.com");
            assertThat(result.file().isFavorite()).isTrue();
        }

        @Test
        @DisplayName("이 파일 자체엔 직접 공유가 없고 상위 폴더를 통해서만 접근 가능해도 공유된 날짜가 채워진다")
        void fillsSharedAtFromAnInheritedGrantWhenTheFileHasNoDirectShare() {
            LocalDateTime sharedAt = LocalDateTime.of(2026, 9, 4, 15, 5);
            FileShare inheritedGrant = grantTo(callerId, sharedAt);
            given(findFilePort.findById(command.getFileId()))
                    .willReturn(Optional.of(fileOwnedBy(otherOwnerId, FileStatus.UPLOADED)));
            given(fileAccessGuard.requirePermission(any(File.class), eq(callerId), eq(Permission.READ)))
                    .willReturn(new FileAccessGuard.Access(Role.VIEWER, inheritedGrant));
            given(findMemberByIdPort.findMemberByIdOrUnknown(otherOwnerId))
                    .willReturn(new MemberSummary("홍길동", "owner@modudrive.com"));

            FileView result = getFileService.getFile(command);

            assertThat(result.sharedAt()).isEqualTo(sharedAt);
        }
    }

    @Nested
    @DisplayName("직접/상속 공유 없이 링크로만 접근한 로그인 사용자가 조회할 때")
    class WhenLinkOnlyVisitor {

        @Test
        @DisplayName("뷰어로 열람은 되지만 소유자 이름/이메일은 응답에서 비어있다 (#319)")
        void hidesTheOwnerIdentity() {
            given(findFilePort.findById(command.getFileId()))
                    .willReturn(Optional.of(fileOwnedBy(otherOwnerId, FileStatus.UPLOADED)));
            // LINK 폴백으로만 VIEWER를 얻은 경우 — 공유 행(grant)이 존재하지 않는다.
            given(fileAccessGuard.requirePermission(any(File.class), eq(callerId), eq(Permission.READ)))
                    .willReturn(new FileAccessGuard.Access(Role.VIEWER, null));

            FileView result = getFileService.getFile(command);

            assertThat(result.callerRole()).isEqualTo(Role.VIEWER);
            assertThat(result.sharedByName()).isNull();
            assertThat(result.sharedByEmail()).isNull();
            assertThat(result.sharedAt()).isNull();
            then(findMemberByIdPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> getFileService.getFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("파일이 삭제된 상태일 때")
    class WhenFileIsDeleted {

        @Test
        void throwsFileAlreadyDeleted() {
            given(findFilePort.findById(command.getFileId()))
                    .willReturn(Optional.of(fileOwnedBy(callerId, FileStatus.TRASHED)));

            Throwable thrown = catchThrowable(() -> getFileService.getFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_DELETED);
        }
    }

    @Nested
    @DisplayName("호출자에게 접근 권한이 없을 때")
    class WhenCallerLacksAccess {

        @Test
        void throwsFileAccessDenied() {
            given(findFilePort.findById(command.getFileId()))
                    .willReturn(Optional.of(fileOwnedBy(otherOwnerId, FileStatus.UPLOADED)));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requirePermission(any(File.class), eq(callerId), eq(Permission.READ));

            Throwable thrown = catchThrowable(() -> getFileService.getFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
        }
    }
}
