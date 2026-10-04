package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.usecase.CompleteResumableUploadUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CompleteResumableUploadControllerTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    private MockMvc mockMvc;

    @Mock private CompleteResumableUploadUseCase completeResumableUploadUseCase;
    @InjectMocks private CompleteResumableUploadController completeResumableUploadController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(completeResumableUploadController)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/storage/upload/resumable/{sessionId}/complete")
    class CompleteResumableUpload {

        @Test
        void returnsOkOnSuccess() throws Exception {
            willDoNothing().given(completeResumableUploadUseCase).completeResumableUpload(any());

            mockMvc.perform(post("/api/v1/storage/upload/resumable/" + UUID.randomUUID() + "/complete")
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));
        }
    }
}
