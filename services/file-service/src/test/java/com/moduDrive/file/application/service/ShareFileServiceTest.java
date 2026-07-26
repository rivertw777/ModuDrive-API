package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileStatus;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ShareFileServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private SaveFileSharePort saveFileSharePort;
    @InjectMocks private ShareFileService shareFileService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID sharedWithUserId = UUID.randomUUID();
    private final ShareFileCommand command = new ShareFileCommand(fileId, UUID.randomUUID(), sharedWithUserId, Permission.READ);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(UUID.randomUUID()),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    @Nested
    @DisplayName("파일이 존재하고 공유되지 않은 경우")
    class WhenShareIsNew {

        @Test
        void savesAndReturnsFileShare() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(any(), any())).willReturn(false);
            given(saveFileSharePort.saveFileShare(any(FileShare.class))).willAnswer(inv -> inv.getArgument(0));

            FileShare result = shareFileService.shareFile(command);

            assertThat(result.getSharedWithUserId()).isEqualTo(sharedWithUserId);
            assertThat(result.getPermission()).isEqualTo(Permission.READ);
            then(saveFileSharePort).should().saveFileShare(any(FileShare.class));
        }
    }

    @Nested
    @DisplayName("이미 공유된 파일인 경우")
    class WhenAlreadyShared {

        @Test
        void throwsFileShareAlreadyExists() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.existsByFileIdAndSharedWithUserId(any(), any())).willReturn(true);

            Throwable thrown = catchThrowable(() -> shareFileService.shareFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS);
            then(saveFileSharePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> shareFileService.shareFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }
}
