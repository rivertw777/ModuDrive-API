package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.usecase.SimpleUploadUseCase;
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
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    private MockMvc mockMvc;

    @Mock private SimpleUploadUseCase simpleUploadUseCase;
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
}
