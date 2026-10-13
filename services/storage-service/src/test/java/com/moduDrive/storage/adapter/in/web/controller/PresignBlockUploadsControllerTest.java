package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.command.PresignBlockUploadsCommand;
import com.moduDrive.storage.application.port.in.usecase.PresignBlockUploadsUseCase;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PresignBlockUploadsControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private MockMvc mockMvc;

    @Mock private PresignBlockUploadsUseCase presignBlockUploadsUseCase;
    @InjectMocks private PresignBlockUploadsController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Test
    void passesTheCallersBlocksInOrderAndAnswersTheUrls() throws Exception {
        given(presignBlockUploadsUseCase.presignBlockUploads(any())).willReturn(List.of("url-1", "url-2"));

        mockMvc.perform(post("/api/v1/storage/blocks/upload-urls")
                        .header("X_USER_ID", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"hash\":\"h1\",\"size\":4},{\"hash\":\"h2\",\"size\":2}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urls[0]").value("url-1"))
                .andExpect(jsonPath("$.data.urls[1]").value("url-2"));

        ArgumentCaptor<PresignBlockUploadsCommand> command = ArgumentCaptor.forClass(PresignBlockUploadsCommand.class);
        then(presignBlockUploadsUseCase).should().presignBlockUploads(command.capture());
        assertThat(command.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(command.getValue().getBlocks()).containsExactly(
                new PresignBlockUploadsCommand.Block("h1", 4), new PresignBlockUploadsCommand.Block("h2", 2));
    }

    @Test
    void rejectsARequestWithoutBlocks() throws Exception {
        mockMvc.perform(post("/api/v1/storage/blocks/upload-urls")
                        .header("X_USER_ID", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        then(presignBlockUploadsUseCase).shouldHaveNoInteractions();
    }

    @Test
    void answersARefusedBlockWithItsStatus() throws Exception {
        given(presignBlockUploadsUseCase.presignBlockUploads(any()))
                .willThrow(new BusinessException(StorageExceptionCase.BLOCK_TOO_LARGE));

        mockMvc.perform(post("/api/v1/storage/blocks/upload-urls")
                        .header("X_USER_ID", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"hash\":\"h1\",\"size\":99999999}]}"))
                .andExpect(status().isPayloadTooLarge());
    }
}
