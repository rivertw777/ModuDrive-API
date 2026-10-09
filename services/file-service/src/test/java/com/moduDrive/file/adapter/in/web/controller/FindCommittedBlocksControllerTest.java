package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.usecase.FindCommittedBlocksUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FindCommittedBlocksController.class)
@Import(GlobalExceptionHandler.class)
class FindCommittedBlocksControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FindCommittedBlocksUseCase findCommittedBlocksUseCase;

    @Test
    void returnsTheCommittedHashes() throws Exception {
        UUID ownerId = UUID.randomUUID();
        given(findCommittedBlocksUseCase.findCommittedBlocks(ownerId, List.of("h1", "h2"))).willReturn(Set.of("h1"));

        mockMvc.perform(post("/internal/files/blocks/committed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + ownerId + "\",\"hashes\":[\"h1\",\"h2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("h1"));
    }
}
