package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UploadFileMetadataCommand;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileOwnerId;
import com.moduDrive.file.domain.model.File.FilePath;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceQuotaBytes;
import com.moduDrive.file.domain.model.Namespace.NamespaceRootPath;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UploadFileMetadataServiceTest {

    @Mock
    private FindNamespacePort findNamespacePort;
    @Mock
    private SaveFilePort saveFilePort;
    @InjectMocks
    private UploadFileMetadataService uploadFileMetadataService;

    private final UUID userId = UUID.randomUUID();
    private final UploadFileMetadataCommand command = new UploadFileMetadataCommand(
            userId, new FileName("report.pdf"), new FilePath("/1/docs"),
            new FileOwnerId(userId), new FileIsDirectory(false));

    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()),
            new NamespaceUserId(userId),
            new NamespaceRootPath("/1"),
            new NamespaceQuotaBytes(21474836480L));

    @Nested
    @DisplayName("네임스페이스가 존재할 때")
    class WhenNamespaceExists {

        @Test
        void createsPendingFile() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = uploadFileMetadataService.uploadFileMetadata(command);

            assertThat(result.getStatus()).isEqualTo(FileStatus.PENDING);
            assertThat(result.getName()).isEqualTo("report.pdf");
            then(saveFilePort).should().saveFile(any(File.class));
        }
    }

    @Nested
    @DisplayName("네임스페이스가 존재하지 않을 때")
    class WhenNamespaceNotFound {

        @Test
        void throwsBusinessException() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> uploadFileMetadataService.uploadFileMetadata(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.NAMESPACE_NOT_FOUND);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }
}
