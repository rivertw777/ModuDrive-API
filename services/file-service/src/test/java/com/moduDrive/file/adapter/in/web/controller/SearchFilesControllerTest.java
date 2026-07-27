package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.SearchFilesCommand;
import com.moduDrive.file.application.port.in.usecase.SearchFilesUseCase;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchFilesController.class)
@Import(GlobalExceptionHandler.class)
class SearchFilesControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SearchFilesUseCase searchFilesUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("GET /api/v1/files/search")
    class SearchFiles {

        @Test
        void returnsMatchingFileList() throws Exception {
            File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/1/docs"),
                    new FileOwnerId(UUID.fromString(USER_ID)), null, null, FileStatus.UPLOADED,
                    new FileIsDirectory(false));
            given(searchFilesUseCase.searchFiles(any(SearchFilesCommand.class))).willReturn(List.of(file));

            mockMvc.perform(get("/api/v1/files/search")
                            .header("X_USER_ID", USER_ID)
                            .param("query", "report"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("report.pdf"));
        }
    }
}
