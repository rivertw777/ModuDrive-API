package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.RestoreFileCommand;
import com.moduDrive.file.application.port.in.usecase.RestoreFileUseCase;
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
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestoreFileController.class)
@Import(GlobalExceptionHandler.class)
class RestoreFileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RestoreFileUseCase restoreFileUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("파일이 삭제된 상태일 때")
    class WhenFileIsDeleted {

        @Test
        void returnsRestoredFile() throws Exception {
            File restored = File.withId(new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/1/docs"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            given(restoreFileUseCase.restoreFile(any(RestoreFileCommand.class))).willReturn(restored);

            mockMvc.perform(patch("/api/v1/files/{fileId}/restore", FILE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("UPLOADED"));
        }
    }

    @Nested
    @DisplayName("파일이 삭제된 상태가 아닐 때")
    class WhenFileNotDeleted {

        @Test
        void returnsBadRequest() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_DELETED))
                    .given(restoreFileUseCase).restoreFile(any(RestoreFileCommand.class));

            mockMvc.perform(patch("/api/v1/files/{fileId}/restore", FILE_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(FileExceptionCase.FILE_NOT_DELETED.getMessage()));
        }
    }
}
