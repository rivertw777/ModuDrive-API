package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.ResolvePublicArchiveEntriesCommand;
import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;
import com.moduDrive.file.application.port.in.usecase.ResolvePublicArchiveEntriesUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResolvePublicArchiveEntriesController.class)
@Import(GlobalExceptionHandler.class)
class ResolvePublicArchiveEntriesControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ResolvePublicArchiveEntriesUseCase resolvePublicArchiveEntriesUseCase;

    @Nested
    @DisplayName("key 없이 요청해도")
    class WhenNoKey {

        @Test
        void returnsEntries() throws Exception {
            given(resolvePublicArchiveEntriesUseCase.resolvePublicArchiveEntries(any(ResolvePublicArchiveEntriesCommand.class)))
                    .willReturn(List.of(new ArchiveEntry("shared/", null)));

            mockMvc.perform(post("/internal/files/public/archive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileIds\":[\"abc\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].path").value("shared/"));
        }
    }
}
