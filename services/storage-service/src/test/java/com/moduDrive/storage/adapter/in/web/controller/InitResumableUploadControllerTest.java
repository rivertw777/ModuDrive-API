package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.usecase.InitResumableUploadUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InitResumableUploadControllerTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    private MockMvc mockMvc;

    @Mock private InitResumableUploadUseCase initResumableUploadUseCase;
    @InjectMocks private InitResumableUploadController initResumableUploadController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(initResumableUploadController)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/storage/upload/resumable")
    class InitResumableUpload {

        @Test
        void returnsSessionIdOnSuccess() throws Exception {
            UUID sessionId = UUID.randomUUID();
            given(initResumableUploadUseCase.initResumableUpload(any())).willReturn(sessionId);

            mockMvc.perform(post("/api/v1/storage/upload/resumable")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":\"" + UUID.randomUUID() + "\",\"totalChunks\":5,\"fileSize\":1000000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(sessionId.toString()));
        }

        @Test
        void returnsBadRequestWhenFileIdBlank() throws Exception {
            mockMvc.perform(post("/api/v1/storage/upload/resumable")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":\"\",\"totalChunks\":3,\"fileSize\":1000000}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returnsBadRequestWhenTotalChunksZero() throws Exception {
            mockMvc.perform(post("/api/v1/storage/upload/resumable")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":\"" + UUID.randomUUID() + "\",\"totalChunks\":0,\"fileSize\":1000000}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
