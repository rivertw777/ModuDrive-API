package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.CreateDirectoryCommand;
import com.moduDrive.file.application.port.in.command.ListDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.CreateDirectoryUseCase;
import com.moduDrive.file.application.port.in.usecase.ListDirectoryUseCase;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DirectoryController.class)
@Import(GlobalExceptionHandler.class)
class DirectoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ListDirectoryUseCase listDirectoryUseCase;
    @MockitoBean private CreateDirectoryUseCase createDirectoryUseCase;

    private final File dirFile = File.withId(
            new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
            new FileName("docs"), new FilePath("/1"),
            new FileOwnerId(1L), null, null,
            FileStatus.PENDING, new FileIsDirectory(true));

    @Nested
    @DisplayName("GET /api/v1/directories")
    class ListDirectory {

        @Test
        void returnsFileList() throws Exception {
            given(listDirectoryUseCase.listDirectory(any(ListDirectoryCommand.class))).willReturn(List.of(dirFile));

            mockMvc.perform(get("/api/v1/directories")
                            .param("userId", "1")
                            .param("path", "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("docs"))
                    .andExpect(jsonPath("$.data[0].directory").value(true));
        }

        @Test
        void returnsNotFoundWhenNamespaceMissing() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND))
                    .given(listDirectoryUseCase).listDirectory(any(ListDirectoryCommand.class));

            mockMvc.perform(get("/api/v1/directories")
                            .param("userId", "1")
                            .param("path", "/1"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/directories")
    class CreateDirectory {

        @Test
        void returnsCreatedDirectory() throws Exception {
            given(createDirectoryUseCase.createDirectory(any(CreateDirectoryCommand.class))).willReturn(dirFile);

            mockMvc.perform(post("/api/v1/directories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId":1,"name":"docs","path":"/1"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.directory").value(true));
        }

        @Test
        void returnsBadRequestOnMissingField() throws Exception {
            mockMvc.perform(post("/api/v1/directories")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
