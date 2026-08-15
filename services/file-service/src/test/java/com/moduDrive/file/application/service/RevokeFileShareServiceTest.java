package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;
import com.moduDrive.file.application.port.out.DeleteFileSharePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.FindFileSharePort;
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
class RevokeFileShareServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FindFileSharePort findFileSharePort;
    @Mock private DeleteFileSharePort deleteFileSharePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private RevokeFileShareService revokeFileShareService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID shareId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final RevokeFileShareCommand command = new RevokeFileShareCommand(fileId, shareId, ownerId);

    private final File file = File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(ownerId),
            null, null, FileStatus.UPLOADED, new FileIsDirectory(false));

    private FileShare share(UUID belongsToFileId) {
        return share(belongsToFileId, Role.VIEWER);
    }

    private FileShare share(UUID belongsToFileId, Role role) {
        return FileShare.withId(new FileShareId(shareId), new FileShareFileId(belongsToFileId),
                new FileShareOwnerId(ownerId), new FileShareSharedWithUserId(UUID.randomUUID()),
                new FileShareRole(role));
    }

    @Nested
    @DisplayName("소유자가 자기 파일의 공유를 해제할 때")
    class WhenOwnerRevokesOwnFileShare {

        @Test
        void deletesShare() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(file));
            given(findFileSharePort.findByShareId(command.getShareId())).willReturn(Optional.of(share(fileId)));

            revokeFileShareService.revokeFileShare(command);

            then(deleteFileSharePort).should().deleteFileShare(new FileShareId(shareId));
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

            Throwable thrown = catchThrowable(() -> revokeFileShareService.revokeFileShare(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_SHARE_NOT_FOUND);
            then(deleteFileSharePort).shouldHaveNoInteractions();
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

            Throwable thrown = catchThrowable(() -> revokeFileShareService.revokeFileShare(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(deleteFileSharePort).shouldHaveNoInteractions();
        }
    }
}
