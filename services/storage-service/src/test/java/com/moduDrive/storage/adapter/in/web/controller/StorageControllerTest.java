package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.usecase.InitResumableUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.SimpleUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.UploadChunkUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private SimpleUploadUseCase simpleUploadUseCase;
    @Mock private InitResumableUploadUseCase initResumableUploadUseCase;
    @Mock private UploadChunkUseCase uploadChunkUseCase;
    @InjectMocks private StorageController storageController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(storageController)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/storage/upload")
    class SimpleUpload {

        @Test
        void returnsOkOnSuccess() throws Exception {
            willDoNothing().given(simpleUploadUseCase).simpleUpload(any());
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "hello".getBytes());

            mockMvc.perform(multipart("/api/v1/storage/upload")
                            .file(file)
                            .param("fileId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));
        }

        @Test
        void returnsBadRequestOnMissingFileId() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "hello".getBytes());

            mockMvc.perform(multipart("/api/v1/storage/upload").file(file))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/storage/upload/resumable")
    class InitResumableUpload {

        @Test
        void returnsSessionIdOnSuccess() throws Exception {
            UUID sessionId = UUID.randomUUID();
            given(initResumableUploadUseCase.initResumableUpload(any())).willReturn(sessionId);

            mockMvc.perform(post("/api/v1/storage/upload/resumable")
                            .header("X_USER_ID", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":\"" + UUID.randomUUID() + "\",\"totalChunks\":5}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(sessionId.toString()));
        }

        @Test
        void returnsBadRequestWhenFileIdBlank() throws Exception {
            mockMvc.perform(post("/api/v1/storage/upload/resumable")
                            .header("X_USER_ID", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":\"\",\"totalChunks\":3}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returnsBadRequestWhenTotalChunksZero() throws Exception {
            mockMvc.perform(post("/api/v1/storage/upload/resumable")
                            .header("X_USER_ID", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":\"" + UUID.randomUUID() + "\",\"totalChunks\":0}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/storage/upload/resumable/{sessionId}")
    class UploadChunk {

        @Test
        void returnsOkOnSuccess() throws Exception {
            willDoNothing().given(uploadChunkUseCase).uploadChunk(any());
            MockMultipartFile chunk = new MockMultipartFile(
                    "chunk", "chunk0.bin", "application/octet-stream", "data".getBytes());

            mockMvc.perform(multipart(PUT, "/api/v1/storage/upload/resumable/" + UUID.randomUUID())
                            .file(chunk)
                            .header("X_USER_ID", 1L)
                            .param("chunkIndex", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));
        }

        @Test
        void returnsBadRequestOnMissingChunkIndex() throws Exception {
            MockMultipartFile chunk = new MockMultipartFile(
                    "chunk", "chunk0.bin", "application/octet-stream", "data".getBytes());

            mockMvc.perform(multipart(PUT, "/api/v1/storage/upload/resumable/" + UUID.randomUUID())
                            .file(chunk)
                            .header("X_USER_ID", 1L))
                    .andExpect(status().isBadRequest());
        }
    }
}
