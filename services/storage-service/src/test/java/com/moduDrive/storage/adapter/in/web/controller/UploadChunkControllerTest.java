package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.usecase.UploadChunkUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UploadChunkControllerTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    private MockMvc mockMvc;

    @Mock private UploadChunkUseCase uploadChunkUseCase;
    @InjectMocks private UploadChunkController uploadChunkController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(uploadChunkController)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
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
                            .header("X_USER_ID", USER_ID)
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
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isBadRequest());
        }
    }
}
