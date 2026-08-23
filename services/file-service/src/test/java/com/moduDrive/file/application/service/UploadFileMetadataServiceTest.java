package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UploadFileMetadataCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.File.FileIsDirectory;
import com.moduDrive.file.domain.model.File.FileName;
import com.moduDrive.file.domain.model.File.FileNamespaceId;
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
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class UploadFileMetadataServiceTest {

    @Mock
    private FindNamespacePort findNamespacePort;
    @Mock
    private FindFilePort findFilePort;
    @Mock
    private SaveFilePort saveFilePort;
    @Mock
    private FileAccessGuard fileAccessGuard;
    @InjectMocks
    private UploadFileMetadataService uploadFileMetadataService;

    private final UUID userId = UUID.randomUUID();
    private final UploadFileMetadataCommand command = new UploadFileMetadataCommand(
            userId, new FileName("report.pdf"), new FilePath("/1/docs"),
            new FileOwnerId(userId), new FileIsDirectory(false), false);
    private final UploadFileMetadataCommand replaceCommand = new UploadFileMetadataCommand(
            userId, new FileName("report.pdf"), new FilePath("/1/docs"),
            new FileOwnerId(userId), new FileIsDirectory(false), true);

    private final Namespace namespace = Namespace.withId(
            new NamespaceId(UUID.randomUUID()),
            new NamespaceUserId(userId),
            new NamespaceRootPath("/1"),
            new NamespaceQuotaBytes(21474836480L));

    @Nested
    @DisplayName("네임스페이스가 존재하고 같은 이름의 활성 파일이 없을 때")
    class WhenNamespaceExistsAndNoConflict {

        @Test
        void createsPendingFile() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(any(), any(), any()))
                    .willReturn(Optional.empty());
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = uploadFileMetadataService.uploadFileMetadata(command);

            assertThat(result.getStatus()).isEqualTo(FileStatus.PENDING);
            assertThat(result.getName()).isEqualTo("report.pdf");
            then(saveFilePort).should().saveFile(any(File.class));
        }

        @Test
        @DisplayName("휴지통에 같은 이름의 파일이 있어도(활성 파일이 아니므로) 새 파일로 만든다")
        void createsANewFileEvenWhenATrashedOneSharesTheName() {
            // findActiveByNamespaceIdAndPathAndName excludes DELETED rows by contract, so a
            // trashed same-name file simply never surfaces here — nothing to stub differently.
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(any(), any(), any()))
                    .willReturn(Optional.empty());
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = uploadFileMetadataService.uploadFileMetadata(command);

            assertThat(result.getId()).isNull();
            assertThat(result.getStatus()).isEqualTo(FileStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("같은 이름/경로의 활성 파일이 이미 있고, 대체에 동의했을 때")
    class WhenSameNameFileExistsAndReplaceIsConsented {

        private final File uploadedFile = File.withId(
                new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                new FileName("report.pdf"), new FilePath("/1/docs"), new FileOwnerId(userId),
                null, new File.FileSize(1024L), FileStatus.UPLOADED, new FileIsDirectory(false));

        @Test
        void reusesExistingFileAsPendingNewVersion() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(any(), any(), any()))
                    .willReturn(Optional.of(uploadedFile));
            given(saveFilePort.saveFile(any(File.class))).willAnswer(inv -> inv.getArgument(0));

            File result = uploadFileMetadataService.uploadFileMetadata(replaceCommand);

            assertThat(result.getId()).isEqualTo(uploadedFile.getId());
            assertThat(result.getStatus()).isEqualTo(FileStatus.PENDING);
            then(fileAccessGuard).should().requireOwner(uploadedFile, userId);
        }

        @Test
        @DisplayName("호출자가 그 파일의 소유자가 아니면 가드가 거부한다")
        void deniedWhenCallerIsNotTheOwner() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(any(), any(), any()))
                    .willReturn(Optional.of(uploadedFile));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(uploadedFile, userId);

            Throwable thrown = catchThrowable(() -> uploadFileMetadataService.uploadFileMetadata(replaceCommand));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("같은 이름/경로의 활성 파일이 이미 있지만, 대체에 동의하지 않았을 때")
    class WhenSameNameFileExistsWithoutReplaceConsent {

        private final File uploadedFile = File.withId(
                new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                new FileName("report.pdf"), new FilePath("/1/docs"), new FileOwnerId(userId),
                null, new File.FileSize(1024L), FileStatus.UPLOADED, new FileIsDirectory(false));

        @Test
        void throwsBusinessException() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(any(), any(), any()))
                    .willReturn(Optional.of(uploadedFile));

            Throwable thrown = catchThrowable(() -> uploadFileMetadataService.uploadFileMetadata(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_EXISTS);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("같은 이름의 디렉터리가 이미 존재할 때")
    class WhenSameNameDirectoryAlreadyExists {

        private final File existingDirectory = File.withId(
                new FileId(UUID.randomUUID()), new FileNamespaceId(namespace.getId()),
                new FileName("report.pdf"), new FilePath("/1/docs"), new FileOwnerId(userId),
                null, new File.FileSize(0L), FileStatus.UPLOADED, new FileIsDirectory(true));

        @Test
        @DisplayName("대체에 동의했어도 타입이 다르면 거부한다")
        void throwsBusinessExceptionEvenWithReplaceConsent() {
            given(findNamespacePort.findByUserId(any())).willReturn(Optional.of(namespace));
            given(findFilePort.findActiveByNamespaceIdAndPathAndName(any(), any(), any()))
                    .willReturn(Optional.of(existingDirectory));

            Throwable thrown = catchThrowable(() -> uploadFileMetadataService.uploadFileMetadata(replaceCommand));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_EXISTS);
            then(saveFilePort).shouldHaveNoInteractions();
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
