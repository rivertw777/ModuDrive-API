package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.ListRecentFilesCommand;
import com.moduDrive.file.application.port.in.usecase.ListRecentFilesUseCase;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListRecentFilesController.class)
@Import(GlobalExceptionHandler.class)
class ListRecentFilesControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ListRecentFilesUseCase listRecentFilesUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("GET /api/v1/files/recent")
    class ListRecentFiles {

        @Test
        void returnsRecentFileListInOrder() throws Exception {
            File recent = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/1/docs"),
                    new FileOwnerId(UUID.fromString(USER_ID)), null, null, FileStatus.UPLOADED,
                    new FileIsDirectory(false));
            given(listRecentFilesUseCase.listRecentFiles(any(ListRecentFilesCommand.class)))
                    .willReturn(List.of(recent));

            mockMvc.perform(get("/api/v1/files/recent").header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("report.pdf"));
        }

        @Test
        void defaultsLimitToTwenty() throws Exception {
            given(listRecentFilesUseCase.listRecentFiles(any(ListRecentFilesCommand.class))).willReturn(List.of());

            mockMvc.perform(get("/api/v1/files/recent").header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk());

            then(listRecentFilesUseCase).should()
                    .listRecentFiles(argThat(cmd -> cmd.getLimit() == 20));
        }

        @Test
        void rejectsZeroLimit() throws Exception {
            mockMvc.perform(get("/api/v1/files/recent").param("limit", "0").header("X_USER_ID", USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rejectsOversizedLimit() throws Exception {
            mockMvc.perform(get("/api/v1/files/recent").param("limit", "1000000").header("X_USER_ID", USER_ID))
                    .andExpect(status().isBadRequest());
        }
    }
}
