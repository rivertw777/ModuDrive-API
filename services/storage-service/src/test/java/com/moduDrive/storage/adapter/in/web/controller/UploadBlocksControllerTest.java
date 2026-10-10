package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.command.UploadBlocksCommand;
import com.moduDrive.storage.application.port.in.usecase.UploadBlocksUseCase;
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
class UploadBlocksControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private MockMvc mockMvc;

    @Mock private UploadBlocksUseCase uploadBlocksUseCase;
    @InjectMocks private UploadBlocksController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    private static MockMultipartFile block(String content) {
        return new MockMultipartFile("block", "blob", "application/octet-stream", content.getBytes());
    }

    @Test
    void pairsEachHashWithItsBlockInOrder() throws Exception {
        mockMvc.perform(multipart("/api/v1/storage/blocks")
                        .file(block("one")).file(block("two"))
                        .param("hash", "h1", "h2")
                        .header("X_USER_ID", USER_ID.toString()))
                .andExpect(status().isOk());

        ArgumentCaptor<UploadBlocksCommand> command = ArgumentCaptor.forClass(UploadBlocksCommand.class);
        then(uploadBlocksUseCase).should().uploadBlocks(command.capture());
        assertThat(command.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(command.getValue().getBlocks())
                .extracting(UploadBlocksCommand.Block::hash)
                .containsExactly("h1", "h2");
        assertThat(command.getValue().getBlocks().get(1).data()).isEqualTo("two".getBytes());
    }

    @Test
    void rejectsHashesAndBlocksThatDoNotPairUp() throws Exception {
        mockMvc.perform(multipart("/api/v1/storage/blocks")
                        .file(block("one"))
                        .param("hash", "h1", "h2")
                        .header("X_USER_ID", USER_ID.toString()))
                .andExpect(status().isBadRequest());

        then(uploadBlocksUseCase).shouldHaveNoInteractions();
    }

    @Test
    void answersARejectedBlockWith400() throws Exception {
        willThrow(new BusinessException(StorageExceptionCase.INVALID_BLOCK)).given(uploadBlocksUseCase).uploadBlocks(any());

        mockMvc.perform(multipart("/api/v1/storage/blocks")
                        .file(block("one"))
                        .param("hash", "h1")
                        .header("X_USER_ID", USER_ID.toString()))
                .andExpect(status().isBadRequest());
    }
}
