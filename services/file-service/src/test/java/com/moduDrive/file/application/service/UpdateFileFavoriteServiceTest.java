package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileFavoriteCommand;
import com.moduDrive.file.application.port.out.FileFavoritePort;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class UpdateFileFavoriteServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private FileFavoritePort fileFavoritePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private UpdateFileFavoriteService updateFileFavoriteService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID otherOwnerId = UUID.randomUUID();

    private File fileOwnedBy(UUID ownerId) {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(ownerId), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
    }

    @Nested
    @DisplayName("소유자가 자기 파일을 즐겨찾기할 때")
    class WhenOwnerFavoritesOwnFile {

        @Test
        void writesAFileFavoriteRowWithoutTouchingTheFile() {
            var command = new UpdateFileFavoriteCommand(fileId, callerId, true);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(fileOwnedBy(callerId)));

            File result = updateFileFavoriteService.updateFavorite(command);

            then(fileFavoritePort).should().favorite(callerId, fileId);
            then(fileAccessGuard).shouldHaveNoInteractions();
            assertThat(result.isFavorite()).isTrue();
        }

        @Test
        void removesTheFileFavoriteRowWhenUnstarring() {
            var command = new UpdateFileFavoriteCommand(fileId, callerId, false);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(fileOwnedBy(callerId)));

            File result = updateFileFavoriteService.updateFavorite(command);

            then(fileFavoritePort).should().unfavorite(callerId, fileId);
            assertThat(result.isFavorite()).isFalse();
        }
    }

    @Nested
    @DisplayName("공유받은 사용자가 즐겨찾기할 때")
    class WhenGranteeFavoritesSharedFile {

        @Test
        void requiresReadPermissionThenWritesAPerUserFavorite() {
            var command = new UpdateFileFavoriteCommand(fileId, callerId, true);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(fileOwnedBy(otherOwnerId)));

            File result = updateFileFavoriteService.updateFavorite(command);

            then(fileAccessGuard).should().requirePermission(any(File.class), eq(callerId), eq(Permission.READ));
            then(fileFavoritePort).should().favorite(callerId, fileId);
            assertThat(result.isFavorite()).isTrue();
        }

        @Test
        void throwsFileAccessDeniedWhenCallerCannotReadTheFile() {
            var command = new UpdateFileFavoriteCommand(fileId, callerId, true);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(fileOwnedBy(otherOwnerId)));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requirePermission(any(File.class), eq(callerId), eq(Permission.READ));

            Throwable thrown = catchThrowable(() -> updateFileFavoriteService.updateFavorite(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(fileFavoritePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            var command = new UpdateFileFavoriteCommand(fileId, callerId, true);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> updateFileFavoriteService.updateFavorite(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
            then(fileFavoritePort).shouldHaveNoInteractions();
        }
    }
}
