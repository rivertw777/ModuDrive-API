package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.UpdateFileFavoriteCommand;
import com.moduDrive.file.application.port.out.FindFilePort;
import com.moduDrive.file.application.port.out.SaveFilePort;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class UpdateFileFavoriteServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private FileAccessGuard fileAccessGuard;
    @InjectMocks private UpdateFileFavoriteService updateFileFavoriteService;

    private final UUID fileId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    private File makeFile() {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(callerId), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
    }

    @Nested
    @DisplayName("파일이 존재할 때")
    class WhenFileExists {

        @Test
        void marksFileAsFavorite() {
            UpdateFileFavoriteCommand command = new UpdateFileFavoriteCommand(fileId, callerId, true);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile()));
            given(saveFilePort.saveFile(any())).willAnswer(inv -> inv.getArgument(0));

            File result = updateFileFavoriteService.updateFavorite(command);

            assertThat(result.isFavorite()).isTrue();
            then(saveFilePort).should().saveFile(any(File.class));
        }

        @Test
        void unmarksFileAsFavorite() {
            UpdateFileFavoriteCommand command = new UpdateFileFavoriteCommand(fileId, callerId, false);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile()));
            given(saveFilePort.saveFile(any())).willAnswer(inv -> inv.getArgument(0));

            File result = updateFileFavoriteService.updateFavorite(command);

            assertThat(result.isFavorite()).isFalse();
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            UpdateFileFavoriteCommand command = new UpdateFileFavoriteCommand(fileId, callerId, true);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> updateFileFavoriteService.updateFavorite(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("호출자에게 접근 권한이 없을 때")
    class WhenCallerLacksAccess {

        @Test
        void throwsFileAccessDenied() {
            UpdateFileFavoriteCommand command = new UpdateFileFavoriteCommand(fileId, callerId, true);
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile()));
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(fileAccessGuard).requireOwner(any(File.class), eq(callerId));

            Throwable thrown = catchThrowable(() -> updateFileFavoriteService.updateFavorite(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ACCESS_DENIED);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }
}
