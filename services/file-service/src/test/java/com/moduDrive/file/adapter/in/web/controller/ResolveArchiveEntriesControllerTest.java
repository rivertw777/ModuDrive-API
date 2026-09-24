package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.ResolveArchiveEntriesCommand;
import com.moduDrive.file.application.port.in.usecase.ArchiveEntry;
import com.moduDrive.file.application.port.in.usecase.ResolveArchiveEntriesUseCase;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResolveArchiveEntriesController.class)
@Import(GlobalExceptionHandler.class)
class ResolveArchiveEntriesControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ResolveArchiveEntriesUseCase resolveArchiveEntriesUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("요청이 올바를 때")
    class WhenValid {

        @Test
        void returnsEntriesWithDirectoriesHavingNoLocation() throws Exception {
            FileVersion v = FileVersion.withId(new FileVersionId(UUID.randomUUID()), new FileVersionFileId(FILE_ID),
                    new FileVersionFileSize(5L), new FileVersionBlockCount(1), new FileVersionS3Path("s3/k"));
            given(resolveArchiveEntriesUseCase.resolveArchiveEntries(any(ResolveArchiveEntriesCommand.class)))
                    .willReturn(List.of(new ArchiveEntry("docs/", null), new ArchiveEntry("docs/a.txt", v)));

            mockMvc.perform(post("/internal/files/archive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"fileIds\":[\"" + FILE_ID + "\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].path").value("docs/"))
                    .andExpect(jsonPath("$.data[0].s3Path").doesNotExist())
                    .andExpect(jsonPath("$.data[1].s3Path").value("s3/k"))
                    .andExpect(jsonPath("$.data[1].fileId").value(FILE_ID.toString()));
        }
    }

    @Nested
    @DisplayName("고른 항목이 비어 있을 때")
    class WhenEmpty {

        @Test
        void returnsBadRequest() throws Exception {
            mockMvc.perform(post("/internal/files/archive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"fileIds\":[]}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
