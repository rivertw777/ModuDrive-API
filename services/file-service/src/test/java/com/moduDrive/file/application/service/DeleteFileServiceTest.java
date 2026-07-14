package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.DeleteFileCommand;
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

@ExtendWith(MockitoExtension.class)
class DeleteFileServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @InjectMocks private DeleteFileService deleteFileService;

    private final UUID fileId = UUID.randomUUID();
    private final DeleteFileCommand command = new DeleteFileCommand(fileId);

    private File makeFile(FileStatus status) {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(1L), null, null, status, new FileIsDirectory(false));
    }

    @Nested
    @DisplayName("파일이 UPLOADED 상태일 때")
    class WhenFileIsUploaded {

        @Test
        void softDeletesFile() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile(FileStatus.UPLOADED)));

            deleteFileService.deleteFile(command);

            then(saveFilePort).should().saveFile(any(File.class));
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> deleteFileService.deleteFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("파일이 이미 삭제된 상태일 때")
    class WhenFileAlreadyDeleted {

        @Test
        void throwsFileAlreadyDeleted() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile(FileStatus.DELETED)));

            Throwable thrown = catchThrowable(() -> deleteFileService.deleteFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_DELETED);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }
}
