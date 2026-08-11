package com.moduDrive.file.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.command.MoveFileCommand;
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
class MoveFileServiceTest {

    @Mock private FindFilePort findFilePort;
    @Mock private SaveFilePort saveFilePort;
    @Mock private DirectoryCascader directoryCascader;
    @InjectMocks private MoveFileService moveFileService;

    private final UUID fileId = UUID.randomUUID();
    private final MoveFileCommand command = new MoveFileCommand(fileId, "/1/archive");

    private File makeFile(FileStatus status) {
        return makeFile(status, new FileIsDirectory(false));
    }

    private File makeFile(FileStatus status, FileIsDirectory isDirectory) {
        return File.withId(new FileId(fileId), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1/docs"),
                new FileOwnerId(UUID.randomUUID()), null, null, status, isDirectory);
    }

    @Nested
    @DisplayName("파일이 존재할 때")
    class WhenFileExists {

        @Test
        void movesAndSavesFile() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile(FileStatus.UPLOADED)));
            given(saveFilePort.saveFile(any())).willAnswer(inv -> inv.getArgument(0));

            File result = moveFileService.moveFile(command);

            assertThat(result.getPath()).isEqualTo("/1/archive");
            then(saveFilePort).should().saveFile(any(File.class));
            then(directoryCascader).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("이동 대상이 디렉토리일 때")
    class WhenFileIsDirectory {

        @Test
        void cascadesDescendantPaths() {
            given(findFilePort.findById(command.getFileId()))
                    .willReturn(Optional.of(makeFile(FileStatus.UPLOADED, new FileIsDirectory(true))));
            given(saveFilePort.saveFile(any())).willAnswer(inv -> inv.getArgument(0));

            File result = moveFileService.moveFile(command);

            // old full path "/1/docs/report.pdf" -> new full path "/1/archive/report.pdf"
            then(directoryCascader).should()
                    .movePath(any(), eq("/1/docs/report.pdf"), eq("/1/archive/report.pdf"));
        }

        @Test
        void rejectsMovingIntoOwnSubtree() {
            MoveFileCommand selfMove = new MoveFileCommand(fileId, "/1/docs/report.pdf/nested");
            given(findFilePort.findById(selfMove.getFileId()))
                    .willReturn(Optional.of(makeFile(FileStatus.UPLOADED, new FileIsDirectory(true))));

            Throwable thrown = catchThrowable(() -> moveFileService.moveFile(selfMove));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.INVALID_MOVE_TARGET);
            then(saveFilePort).shouldHaveNoInteractions();
            then(directoryCascader).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void throwsFileNotFound() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.empty());

            Throwable thrown = catchThrowable(() -> moveFileService.moveFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_NOT_FOUND);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 이미 삭제된 상태일 때")
    class WhenFileAlreadyDeleted {

        @Test
        void throwsFileAlreadyDeleted() {
            given(findFilePort.findById(command.getFileId())).willReturn(Optional.of(makeFile(FileStatus.DELETED)));

            Throwable thrown = catchThrowable(() -> moveFileService.moveFile(command));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(FileExceptionCase.FILE_ALREADY_DELETED);
            then(saveFilePort).shouldHaveNoInteractions();
        }
    }
}
