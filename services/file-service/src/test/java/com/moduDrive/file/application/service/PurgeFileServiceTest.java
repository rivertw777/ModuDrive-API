package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.PurgeFileCommand;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PurgeFileServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private DirectoryCascader directoryCascader;
    @InjectMocks private PurgeFileService purgeFileService;

    private final UUID fileId = UUID.randomUUID();
    private final PurgeFileCommand command = new PurgeFileCommand(fileId);

    private File makeFile(FileStatus status) {
        return makeFile(status, new FileIsDirectory(false));
    }

    private File makeFile(FileStatus status, FileIsDirectory isDirectory) {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(UUID.randomUUID()), null, null, status, isDirectory);
    }

    @Nested
    @DisplayName("파일이 삭제된 상태일 때")
    class WhenFileIsDeleted {

        @Test
        void purgesFile() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile(FileStatus.DELETED)));

            purgeFileService.purgeFile(command);

            then(saveFilePort).should().deleteFile(command.getFileId());
            then(directoryCascader).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("삭제 대상이 디렉토리일 때")
    class WhenFileIsDirectory {

        @Test
        void cascadesPurgeToDescendants() {
            given(findFilePort.findById(command.getFileId()))
                    .willReturn(Optional.of(makeFile(FileStatus.DELETED, new FileIsDirectory(true))));

            purgeFileService.purgeFile(command);

            then(directoryCascader).should().purge(any(), eq("/1/docs/report.pdf"));
            then(saveFilePort).should().deleteFile(command.getFileId());
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> purgeFileService.purgeFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 삭제된 상태가 아닐 때")
    class WhenFileNotDeleted {

        @Test
        void throwsFileNotDeleted() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile(FileStatus.UPLOADED)));

            Throwable thrown = catchThrowable(() -> purgeFileService.purgeFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_DELETED);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }
}
