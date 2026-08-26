package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.command.RenameFileCommand;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.application.port.in.usecase.RenameFileUseCase;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RenameFileController.class)
@Import(GlobalExceptionHandler.class)
class RenameFileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RenameFileUseCase renameFileUseCase;
    @MockitoBean private RecordFileAccessUseCase recordFileAccessUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("파일이 존재할 때")
    class WhenFileExists {

        @Test
        void returnsRenamedFile() throws Exception {
            File renamed = File.withId(new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("renamed.pdf"), new FilePath("/1/docs"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            given(renameFileUseCase.renameFile(any(RenameFileCommand.class))).willReturn(renamed);

            mockMvc.perform(patch("/api/v1/files/{fileId}/name", FILE_ID)
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"renamed.pdf"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("renamed.pdf"));

            then(recordFileAccessUseCase).should().recordAccess(any(RecordFileAccessCommand.class));
        }

        @Test
        void returnsBadRequestOnBlankName() throws Exception {
            mockMvc.perform(patch("/api/v1/files/{fileId}/name", FILE_ID)
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":""}
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
                    .given(renameFileUseCase).renameFile(any(RenameFileCommand.class));

            mockMvc.perform(patch("/api/v1/files/{fileId}/name", FILE_ID)
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"renamed.pdf"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(FileExceptionCase.FILE_NOT_FOUND.getMessage()));

            then(recordFileAccessUseCase).shouldHaveNoInteractions();
        }
    }
}
