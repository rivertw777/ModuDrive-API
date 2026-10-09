package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.command.UploadBlockCommand;
import com.moduDrive.storage.application.port.in.usecase.UploadBlockUseCase;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UploadBlockControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private MockMvc mockMvc;

    @Mock private UploadBlockUseCase uploadBlockUseCase;
    @InjectMocks private UploadBlockController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Test
    void passesTheCallerHashAndBytesThrough() throws Exception {
        mockMvc.perform(multipart("/api/v1/storage/blocks/{hash}", "abc")
                        .file(new MockMultipartFile("block", "blob", "application/octet-stream", "data".getBytes()))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("X_USER_ID", USER_ID.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<UploadBlockCommand> command = ArgumentCaptor.forClass(UploadBlockCommand.class);
        then(uploadBlockUseCase).should().uploadBlock(command.capture());
        assertThat(command.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(command.getValue().getHash()).isEqualTo("abc");
        assertThat(command.getValue().getData()).isEqualTo("data".getBytes());
    }

    @Test
    void answersARejectedBlockWith400() throws Exception {
        willThrow(new BusinessException(StorageExceptionCase.INVALID_BLOCK)).given(uploadBlockUseCase).uploadBlock(any());

        mockMvc.perform(multipart("/api/v1/storage/blocks/{hash}", "abc")
                        .file(new MockMultipartFile("block", "blob", "application/octet-stream", "data".getBytes()))
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("X_USER_ID", USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }
}
