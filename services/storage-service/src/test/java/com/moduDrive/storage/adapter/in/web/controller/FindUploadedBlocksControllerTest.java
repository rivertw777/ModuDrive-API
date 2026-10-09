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
        given(findUploadedBlocksUseCase.findUploadedBlocks(ownerId, List.of("h1", "h2"))).willReturn(Map.of("h1", 4));

        mockMvc.perform(post("/internal/storage/blocks/uploaded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId + "\",\"hashes\":[\"h1\",\"h2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.h1").value(4));
    }
}
