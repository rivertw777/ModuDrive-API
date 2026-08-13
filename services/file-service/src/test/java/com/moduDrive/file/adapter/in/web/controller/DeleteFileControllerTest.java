package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.DeleteFileCommand;
import com.moduDrive.file.application.port.in.usecase.DeleteFileUseCase;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeleteFileController.class)
@Import(GlobalExceptionHandler.class)
class DeleteFileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DeleteFileUseCase deleteFileUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("파일이 존재할 때")
    class WhenFileExists {

        @Test
        void returnsSuccess() throws Exception {
            mockMvc.perform(delete("/api/v1/files/{fileId}", FILE_ID)
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));

            then(deleteFileUseCase).should().deleteFile(any(DeleteFileCommand.class));
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(deleteFileUseCase).deleteFile(any(DeleteFileCommand.class));

            mockMvc.perform(delete("/api/v1/files/{fileId}", FILE_ID)
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(FileExceptionCase.FILE_NOT_FOUND.getMessage()));
        }
    }
}
