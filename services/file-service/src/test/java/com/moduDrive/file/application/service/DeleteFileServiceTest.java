package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.DeleteFileCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
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
import static org.mockito.BDDMockito.then;

import com.moduDrive.file.domain.model.Role;

import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class DeleteFileServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private DirectoryCascader directoryCascader;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private DeleteFileService deleteFileService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final DeleteFileCommand command = new DeleteFileCommand(fileId, callerId);

    private File makeFile(FileStatus status) {
        return makeFile(status, new FileIsDirectory(false));
    }

    private File makeFile(FileStatus status, FileIsDirectory isDirectory) {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(callerId), null, null, status, isDirectory);
    }

    @Nested
    @DisplayName("파일이 UPLOADED 상태일 때")
    class WhenFileIsUploaded {

        @Test
        void softDeletesFileAndStampsTrashedAt() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile(FileStatus.UPLOADED)));
            given(saveFilePort.saveFile(any())).willAnswer(inv -> inv.getArgument(0));

            deleteFileService.deleteFile(command);

            org.mockito.ArgumentCaptor<File> saved = org.mockito.ArgumentCaptor.forClass(File.class);
            then(saveFilePort).should().saveFile(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(FileStatus.DELETED);
            assertThat(saved.getValue().getTrashedAt()).isNotNull();
            then(directoryCascader).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("삭제 대상이 디렉토리일 때")
    class WhenFileIsDirectory {

        @Test
        void cascadesSoftDeleteWithTheSameTrashedAtAsTheRoot() {
            given(findFilePort.findById(command.getFileId()))
                    .willReturn(Optional.of(makeFile(FileStatus.UPLOADED, new FileIsDirectory(true))));
            given(saveFilePort.saveFile(any())).willAnswer(inv -> inv.getArgument(0));

            deleteFileService.deleteFile(command);

            org.mockito.ArgumentCaptor<File> rootSaved = org.mockito.ArgumentCaptor.forClass(File.class);
            org.mockito.ArgumentCaptor<java.time.LocalDateTime> cascadeTrashedAt =
                    org.mockito.ArgumentCaptor.forClass(java.time.LocalDateTime.class);
            then(saveFilePort).should().saveFile(rootSaved.capture());
            then(directoryCascader).should()
                    .softDelete(any(), eq("/1/docs/report.pdf"), cascadeTrashedAt.capture());
            // The whole subtree must share one instant — DirectoryCascader.purge tells a cascade
            // sibling from a later file at a reused path by that equality.
            assertThat(cascadeTrashedAt.getValue()).isEqualTo(rootSaved.getValue().getTrashedAt());
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> deleteFileService.deleteFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("파일이 이미 삭제된 상태일 때")
    class WhenFileAlreadyDeleted {

        @Test
        void throwsFileAlreadyDeleted() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile(FileStatus.DELETED)));

            Throwable thrown = catchThrowable(() -> deleteFileService.deleteFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_DELETED);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("호출자에게 접근 권한이 없을 때")
    class WhenCallerLacksAccess {

        @Test
        void throwsFileAccessDenied() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile(FileStatus.UPLOADED)));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(any(File.class), eq(callerId));

            Throwable thrown = catchThrowable(() -> deleteFileService.deleteFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }
}
