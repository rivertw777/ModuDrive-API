package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetLatestFileVersionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetLatestFileVersionsUseCase;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
import com.moduDrive.file.exception.FileExceptionCase;
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
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GetLatestFileVersionsController.class)
@Import(GlobalExceptionHandler.class)
class GetLatestFileVersionsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetLatestFileVersionsUseCase getLatestFileVersionsUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("최신 버전이 존재할 때 (X_USER_ID 없이 서비스 간 호출)")
    class WhenLatestVersionExists {

        @Test
        void returnsVersionListWithoutRequiringAuthHeader() throws Exception {
            FileVersion v = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(FILE_ID), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));
            given(getLatestFileVersionsUseCase.getLatestFileVersions(any(GetLatestFileVersionsCommand.class)))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/internal/files/{fileId}/revisions", FILE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].s3Path").value("s3://b/k"));
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(getLatestFileVersionsUseCase).getLatestFileVersions(any(GetLatestFileVersionsCommand.class));

            mockMvc.perform(get("/internal/files/{fileId}/revisions", FILE_ID))
                    .andExpect(status().isNotFound());
        }
    }
}
