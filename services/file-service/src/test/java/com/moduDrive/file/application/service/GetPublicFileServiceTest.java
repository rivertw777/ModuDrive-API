package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetPublicFileCommand;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

/** Token validation itself lives in {@link PublicFileResolverTest}; this only pins that the use
 * case defers to it rather than re-deriving the rules. */
@ExtendWith(MockitoExtension.class)
class GetPublicFileServiceTest {

    @Mock private PublicFileResolver publicFileResolver;
    @InjectMocks private GetPublicFileService getPublicFileService;

    private final String token = UUID.randomUUID().toString();
    private final GetPublicFileCommand command = new GetPublicFileCommand(token);

    @Nested
    @DisplayName("토큰이 공개 파일을 가리킬 때")
    class WhenTokenResolves {

        @Test
        void returnsTheResolvedFile() {
            File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(UUID.randomUUID()),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            given(publicFileResolver.resolve(token)).willReturn(file);

            assertThat(getPublicFileService.getPublicFile(command).getName()).isEqualTo("report.pdf");
        }
    }

    @Nested
    @DisplayName("토큰이 유효하지 않을 때")
    class WhenTokenIsRejected {

        @Test
        void propagatesFileNotFound() {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(publicFileResolver).resolve(token);

            Throwable thrown = catchThrowable(() -> getPublicFileService.getPublicFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }
}
