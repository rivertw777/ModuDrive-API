package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetLatestFileVersionsCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
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
class GetLatestFileVersionsServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileVersionsPort findFileVersionsPort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private GetLatestFileVersionsService getLatestFileVersionsService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final GetLatestFileVersionsCommand command = new GetLatestFileVersionsCommand(fileId, 1, callerId);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(UUID.randomUUID()),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    @Nested
    @DisplayName("파일이 존재하고 접근 권한이 있을 때")
    class WhenFileExists {

        @Test
        void returnsVersionList() {
            FileVersion v = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(fileId), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));

            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(any(), eq(1))).willReturn(List.of(v));

            List<FileVersion> result = getLatestFileVersionsService.getLatestFileVersions(command);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileId()).isEqualTo(fileId);
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> getLatestFileVersionsService.getLatestFileVersions(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("호출자에게 접근 권한이 없을 때")
    class WhenCallerLacksAccess {

        @Test
        void throwsFileAccessDenied() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requirePermission(any(File.class), eq(callerId), eq(Permission.READ));

            Throwable thrown = catchThrowable(() -> getLatestFileVersionsService.getLatestFileVersions(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(findFileVersionsPort).shouldHaveNoInteractions();
        }
    }
}
