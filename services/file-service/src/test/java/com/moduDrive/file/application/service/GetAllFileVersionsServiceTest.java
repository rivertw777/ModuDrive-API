package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetAllFileVersionsCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class GetAllFileVersionsServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileVersionsPort findFileVersionsPort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private GetAllFileVersionsService getAllFileVersionsService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final GetAllFileVersionsCommand command = new GetAllFileVersionsCommand(fileId, ownerId);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.DELETED, new FileIsDirectory(false));

    @Nested
    @DisplayName("호출자가 파일 소유자일 때")
    class WhenCallerIsOwner {

        @Test
        void returnsEveryVersion() {
            FileVersion v1 = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(fileId), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/v1"));
            FileVersion v2 = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(fileId), new FileVersionFileSize(1024L),
                    new FileVersionBlockCount(2), new FileVersionS3Path("s3://b/v2"));

            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(any(), anyInt()))
                    .willReturn(List.of(v1, v2));

            List<FileVersion> result = getAllFileVersionsService.getAllFileVersions(command);

            assertThat(result).containsExactly(v1, v2);
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> getAllFileVersionsService.getAllFileVersions(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("호출자가 파일 소유자가 아닐 때")
    class WhenCallerIsNotOwner {

        @Test
        void throwsFileAccessDenied() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(any(File.class), any());

            Throwable thrown = catchThrowable(() -> getAllFileVersionsService.getAllFileVersions(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(findFileVersionsPort).shouldHaveNoInteractions();
        }
    }
}
