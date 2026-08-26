package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetFileCommand;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.usecase.GetFileUseCase;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GetFileController.class)
@Import(GlobalExceptionHandler.class)
class GetFileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetFileUseCase getFileUseCase;
    @MockitoBean private RecordFileAccessUseCase recordFileAccessUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final File uploadedFile = File.withId(
            new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1/docs"),
            new FileOwnerId(UUID.randomUUID()), new FileCurrentVersionId(UUID.randomUUID()),
            new FileSize(1024L), FileStatus.UPLOADED, new FileIsDirectory(false));

    @Nested
    @DisplayName("파일이 존재할 때")
    class WhenFileExists {

        @Test
        void returnsFileInfo() throws Exception {
            given(getFileUseCase.getFile(any(GetFileCommand.class))).willReturn(uploadedFile);

            mockMvc.perform(get("/api/v1/files/{fileId}", FILE_ID).header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("report.pdf"))
                    .andExpect(jsonPath("$.data.status").value("UPLOADED"));

            then(recordFileAccessUseCase).should().recordAccess(any(RecordFileAccessCommand.class));
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(getFileUseCase).getFile(any(GetFileCommand.class));

            mockMvc.perform(get("/api/v1/files/{fileId}", FILE_ID).header("X_USER_ID", USER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(FileExceptionCase.FILE_NOT_FOUND.getMessage()));

            then(recordFileAccessUseCase).shouldHaveNoInteractions();
        }
    }
}
