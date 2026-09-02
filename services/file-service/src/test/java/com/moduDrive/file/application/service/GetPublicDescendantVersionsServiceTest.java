package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.GetPublicDescendantVersionsCommand;
import com.moduDrive.file.application.port.out.FindFileVersionsPort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
import com.moduDrive.file.exception.FileExceptionCase;
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
class GetPublicDescendantVersionsServiceTest {

    @Mock private PublicFileResolver publicFileResolver;
    @Mock private FindFileVersionsPort findFileVersionsPort;
    @InjectMocks private GetPublicDescendantVersionsService service;

    private final UUID fileId = UUID.randomUUID();
    private final String token = UUID.randomUUID().toString();
    private final String entryId = fileId.toString();
    private final GetPublicDescendantVersionsCommand command =
            new GetPublicDescendantVersionsCommand(token, entryId, 1);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("a.txt"), new FilePath("/shared"), new FileOwnerId(UUID.randomUUID()),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    @Test
    void returnsVersionsForTheResolvedDescendant() {
        FileVersion version = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                new FileVersionFileId(fileId), new FileVersionFileSize(512L),
                new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));
        given(publicFileResolver.resolveDescendant(token, entryId)).willReturn(file);
        given(findFileVersionsPort.findByFileIdOrderByCreatedAtDesc(new FileId(fileId), 1))
                .willReturn(List.of(version));

        assertThat(service.getPublicDescendantVersions(command)).containsExactly(version);
    }

    @Test
    void propagatesFileNotFoundWithoutReadingVersions() {
        willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                .given(publicFileResolver).resolveDescendant(token, entryId);

        Throwable thrown = catchThrowable(() -> service.getPublicDescendantVersions(command));

        assertThat(thrown).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getExceptionCase())
                .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        then(findFileVersionsPort).shouldHaveNoInteractions();
    }
}
