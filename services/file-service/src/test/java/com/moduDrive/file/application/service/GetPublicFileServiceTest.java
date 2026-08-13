package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetPublicFileCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GetPublicFileServiceTest {

    @Mock private FindFilePort findFilePort;
    @InjectMocks private GetPublicFileService getPublicFileService;

    private final UUID token = UUID.randomUUID();
    private final GetPublicFileCommand command = new GetPublicFileCommand(token.toString());

    private File makeFile(FileStatus status) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(UUID.randomUUID()),
                null, null, status, new FileIsDirectory(false));
    }

    private void assertNotFound(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getExceptionCase())
                .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
    }

    @Nested
    @DisplayName("토큰이 LINK 공개 파일을 가리킬 때")
    class WhenTokenMatchesLinkSharedFile {

        @Test
        void returnsFile() {
            File file = makeFile(FileStatus.UPLOADED);
            file.enableLinkSharing(token);
            given(findFilePort.findByLinkToken(token)).willReturn(Optional.of(file));

            File result = getPublicFileService.getPublicFile(command);

            assertThat(result.getName()).isEqualTo("report.pdf");
        }
    }

    @Nested
    @DisplayName("파일의 공유 범위가 RESTRICTED로 돌아갔을 때")
    class WhenScopeIsRestricted {

        @Test
        void throwsFileNotFoundWithoutLeakingThatTokenMatched() {
            given(findFilePort.findByLinkToken(token)).willReturn(Optional.of(makeFile(FileStatus.UPLOADED)));

            assertNotFound(catchThrowable(() -> getPublicFileService.getPublicFile(command)));
        }
    }

    @Nested
    @DisplayName("공개된 파일이 휴지통으로 갔을 때")
    class WhenFileIsDeleted {

        @Test
        void throwsFileNotFound() {
            File file = makeFile(FileStatus.DELETED);
            file.enableLinkSharing(token);
            given(findFilePort.findByLinkToken(token)).willReturn(Optional.of(file));

            assertNotFound(catchThrowable(() -> getPublicFileService.getPublicFile(command)));
        }
    }

    @Nested
    @DisplayName("토큰과 일치하는 파일이 없을 때")
    class WhenTokenIsUnknown {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findByLinkToken(token)).willReturn(Optional.empty());

            assertNotFound(catchThrowable(() -> getPublicFileService.getPublicFile(command)));
        }
    }

    @Nested
    @DisplayName("토큰이 UUID 형식이 아닐 때")
    class WhenTokenIsMalformed {

        @Test
        void throwsFileNotFoundWithoutLeakingAFormatError() {
            GetPublicFileCommand malformed = new GetPublicFileCommand("not-a-uuid");

            assertNotFound(catchThrowable(() -> getPublicFileService.getPublicFile(malformed)));
        }
    }
}
