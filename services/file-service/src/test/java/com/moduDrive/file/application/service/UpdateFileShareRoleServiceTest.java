package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileShareRoleCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
import com.moduDrive.file.application.port.out.SaveFileSharePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
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
class UpdateFileShareRoleServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private SaveFileSharePort saveFileSharePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private UpdateFileShareRoleService updateFileShareRoleService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID shareId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UpdateFileShareRoleCommand command =
            new UpdateFileShareRoleCommand(fileId, shareId, ownerId, Role.EDITOR);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    private FileShare share(UUID belongsToFileId) {
        return FileShare.withId(new FileShareId(shareId), new FileShareFileId(belongsToFileId),
                new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(UUID.randomUUID()),
                new FileShareRole(Role.VIEWER));
    }

    @Nested
    @DisplayName("소유자가 자기 파일의 공유 권한을 변경할 때")
    class WhenOwnerUpdatesOwnFileShare {

        @Test
        void savesShareWithNewRole() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));
            given(saveFileSharePort.saveFileShare(any(FileShare.class))).willAnswer(inv -> inv.getArgument(0));

            FileShare result = updateFileShareRoleService.updateFileShareRole(command);

            assertThat(result.getRole()).isEqualTo(Role.EDITOR);
        }
    }

    @Nested
    @DisplayName("공유가 다른 파일에 속할 때")
    class WhenShareBelongsToAnotherFile {

        @Test
        void throwsFileShareNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId()))
                    .willReturn(Optional.of(share(UUID.randomUUID())));

            Throwable thrown = catchThrowable(() -> updateFileShareRoleService.updateFileShareRole(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_NOT_FOUND);
            then(saveFileSharePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("공유가 없을 때")
    class WhenShareNotFound {

        @Test
        void throwsFileShareNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> updateFileShareRoleService.updateFileShareRole(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("호출자가 파일 소유자가 아닐 때")
    class WhenCallerIsNotOwner {

        @Test
        void throwsFileAccessDenied() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(any(File.class), eq(ownerId));

            Throwable thrown = catchThrowable(() -> updateFileShareRoleService.updateFileShareRole(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(saveFileSharePort).shouldHaveNoInteractions();
        }
    }
}
