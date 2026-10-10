package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.usecase.FindUploadedBlocksUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FindUploadedBlocksControllerTest {

    private static final String H1 = "1".repeat(64);
    private static final String H2 = "2".repeat(64);

    private MockMvc mockMvc;

    @Mock private FindUploadedBlocksUseCase findUploadedBlocksUseCase;
    @InjectMocks private FindUploadedBlocksController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Test
    void returnsTheUploadedSizesByHash() throws Exception {
        UUID ownerId = UUID.randomUUID();
        given(findUploadedBlocksUseCase.findUploadedBlocks(ownerId, List.of(H1, H2))).willReturn(Map.of(H1, 4));

        mockMvc.perform(post("/internal/storage/blocks/uploaded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId + "\",\"hashes\":[\"" + H1 + "\",\"" + H2 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data['" + H1 + "']").value(4));
    }

    @Test
    void rejectsAMalformedHash() throws Exception {
        mockMvc.perform(post("/internal/storage/blocks/uploaded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + UUID.randomUUID() + "\",\"hashes\":[\"h1\"]}"))
                .andExpect(status().isBadRequest());
    }
}
