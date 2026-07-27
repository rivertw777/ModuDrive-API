package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.MoveFileCommand;
import com.moduDrive.file.application.port.in.usecase.MoveFileUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MoveFileController.class)
@Import(GlobalExceptionHandler.class)
class MoveFileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MoveFileUseCase moveFileUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("파일이 존재할 때")
    class WhenFileExists {

        @Test
        void returnsMovedFile() throws Exception {
            File moved = File.withId(new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/1/archive"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            given(moveFileUseCase.moveFile(any(MoveFileCommand.class))).willReturn(moved);

            mockMvc.perform(patch("/api/v1/files/{fileId}/path", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"path":"/1/archive"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.path").value("/1/archive"));
        }

        @Test
        void returnsBadRequestOnBlankPath() throws Exception {
            mockMvc.perform(patch("/api/v1/files/{fileId}/path", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"path":""}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(moveFileUseCase).moveFile(any(MoveFileCommand.class));

            mockMvc.perform(patch("/api/v1/files/{fileId}/path", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"path":"/1/archive"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(FileExceptionCase.FILE_NOT_FOUND.getMessage()));
        }
    }
}
