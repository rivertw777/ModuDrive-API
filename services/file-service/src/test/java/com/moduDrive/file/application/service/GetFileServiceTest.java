package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetFileCommand;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort;
import com.moduDrive.file.application.port.out.FindMemberByIdPort.MemberSummary;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class GetFileServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
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
            given(fileAccessGuard.effectiveRole(any(File.class), eq(callerId))).willReturn(Role.EDITOR);
            given(findMemberByIdPort.findMemberById(otherOwnerId))
                    .willReturn(new MemberSummary("홍길동", "owner@modudrive.com"));
            given(findFileSharePort.findByFileIdAndSharedWithUserId(new FileId(fileId), callerId))
                    .willReturn(Optional.empty());

            FileView result = getFileService.getFile(command);

            assertThat(result.callerRole()).isEqualTo(Role.EDITOR);
            assertThat(result.sharedByEmail()).isEqualTo("owner@modudrive.com");
            assertThat(result.file().isFavorite()).isTrue();
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
                    .willReturn(Optional.of(fileOwnedBy(callerId, FileStatus.DELETED)));

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
