package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileStatusCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.application.port.out.SaveFileVersionPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
import com.moduDrive.file.domain.model.Permission;
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
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class UpdateFileStatusServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private SaveFileVersionPort saveFileVersionPort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private UpdateFileStatusService updateFileStatusService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final String s3Path = "files/" + fileId + "/" + UUID.randomUUID();
    private final UpdateFileStatusCommand command =
            new UpdateFileStatusCommand(fileId, ownerId, 1024L, 2, s3Path);

    private final File pendingFile = File.withId(
            new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1/docs"),
            new FileOwnerId(ownerId), null, null,
            FileStatus.PENDING, new FileIsDirectory(false));

    @Nested
    @DisplayName("파일이 존재할 때")
    class WhenFileExists {

        @Test
        void marksFileUploadedAndCreatesVersion() {
            UUID versionId = UUID.randomUUID();
            FileVersion savedVersion = FileVersion.withId(
                    new FileVersionId(versionId),
                    new FileVersionFileId(fileId),
                    new FileVersionFileSize(1024L),
                    new FileVersionBlockCount(2),
                    new FileVersionS3Path(s3Path));

            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(pendingFile));
            given(saveFileVersionPort.saveFileVersion(any())).willReturn(savedVersion);
            given(saveFilePort.saveFile(any())).willAnswer(inv -> inv.getArgument(0));

            File result = updateFileStatusService.updateFileStatus(command);

            assertThat(result.getStatus()).isEqualTo(FileStatus.UPLOADED);
            assertThat(result.getCurrentVersionId()).isEqualTo(versionId);
            then(saveFileVersionPort).should().saveFileVersion(any(FileVersion.class));
            then(saveFilePort).should().saveFile(any(File.class));
        }
    }

    @Nested
    @DisplayName("파일이 존재하지 않을 때")
    class WhenFileNotFound {

        @Test
        void throwsBusinessException() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> updateFileStatusService.updateFileStatus(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
            then(saveFilePort).shouldHaveNoInteractions();
            then(saveFileVersionPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("호출자에게 접근 권한이 없을 때")
    class WhenCallerLacksAccess {

        @Test
        void throwsFileAccessDenied() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(pendingFile));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requirePermission(any(File.class), eq(ownerId), eq(Permission.RENAME));

            Throwable thrown = catchThrowable(() -> updateFileStatusService.updateFileStatus(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(saveFilePort).shouldHaveNoInteractions();
            then(saveFileVersionPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("s3Path가 이 파일 소유의 경로가 아닐 때")
    class WhenS3PathBelongsToAnotherFile {

        @Test
        void throwsFileAccessDenied() {
            var command = new UpdateFileStatusCommand(
                    fileId, ownerId, 1024L, 2, "files/" + UUID.randomUUID() + "/" + UUID.randomUUID());
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(pendingFile));

            Throwable thrown = catchThrowable(() -> updateFileStatusService.updateFileStatus(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(saveFilePort).shouldHaveNoInteractions();
            then(saveFileVersionPort).shouldHaveNoInteractions();
        }
    }
}
