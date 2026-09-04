package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetPublicFileRevisionsCommand;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class GetPublicFileRevisionsServiceTest {

    @Mock private PublicFileResolver publicFileResolver;
    @Mock private FindFileVersionsPort findFileVersionsPort;
    @InjectMocks private GetPublicFileRevisionsService getPublicFileRevisionsService;

    private final UUID fileId = UUID.randomUUID();
    private final String key = UUID.randomUUID().toString();
    private final GetPublicFileRevisionsCommand command =
            new GetPublicFileRevisionsCommand(fileId.toString(), key, 1);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(UUID.randomUUID()),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    @Nested
    @DisplayName("fileId/key가 공개 파일을 가리킬 때")
    class WhenTokenResolves {

        @Test
        void returnsVersionListForThatFile() {
            FileVersion version = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(fileId), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));
            given(publicFileResolver.resolve(fileId.toString(), key)).willReturn(file);
            given(findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(new FileId(fileId), 1))
                    .willReturn(List.of(version));

            List<FileVersion> result = getPublicFileRevisionsService.getPublicFileRevisions(command);

            assertThat(result).containsExactly(version);
        }
    }

    @Nested
    @DisplayName("key가 더 이상 유효하지 않을 때")
    class WhenTokenIsRejected {

        @Test
        void propagatesFileNotFoundWithoutReadingVersions() {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(publicFileResolver).resolve(fileId.toString(), key);

            Throwable thrown = catchThrowable(() -> getPublicFileRevisionsService.getPublicFileRevisions(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
            then(findFileVersionsPort).shouldHaveNoInteractions();
        }
    }
}
