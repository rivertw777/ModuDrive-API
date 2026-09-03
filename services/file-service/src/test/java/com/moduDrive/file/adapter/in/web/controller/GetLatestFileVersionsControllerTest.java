package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetLatestFileVersionsCommand;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.usecase.GetLatestFileVersionsUseCase;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GetLatestFileVersionsController.class)
@Import(GlobalExceptionHandler.class)
class GetLatestFileVersionsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetLatestFileVersionsUseCase getLatestFileVersionsUseCase;
    @MockitoBean private RecordFileAccessUseCase recordFileAccessUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();

    private static final UUID USER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("최신 버전이 존재하고 접근 권한이 있을 때")
    class WhenLatestVersionExists {

        @Test
        void returnsVersionList() throws Exception {
            FileVersion v = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(FILE_ID), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));
            given(getLatestFileVersionsUseCase.getLatestFileVersions(any(GetLatestFileVersionsCommand.class)))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/internal/files/{fileId}/revisions", FILE_ID)
                            .param("userId", USER_ID.toString())
                            .param("markAccessed", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].s3Path").value("s3://b/k"));

            then(recordFileAccessUseCase).should().recordAccess(any(RecordFileAccessCommand.class));
        }

        @Test
        void doesNotRecordAccessWhenMarkAccessedOmitted() throws Exception {
            FileVersion v = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(FILE_ID), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));
            given(getLatestFileVersionsUseCase.getLatestFileVersions(any(GetLatestFileVersionsCommand.class)))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/internal/files/{fileId}/revisions", FILE_ID).param("userId", USER_ID.toString()))
                    .andExpect(status().isOk());

            then(recordFileAccessUseCase).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(getLatestFileVersionsUseCase).getLatestFileVersions(any(GetLatestFileVersionsCommand.class));

            mockMvc.perform(get("/internal/files/{fileId}/revisions", FILE_ID).param("userId", USER_ID.toString()))
                    .andExpect(status().isNotFound());

            then(recordFileAccessUseCase).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("호출자에게 접근 권한이 없을 때")
    class WhenCallerLacksAccess {

        @Test
        void returnsForbidden() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(getLatestFileVersionsUseCase).getLatestFileVersions(any(GetLatestFileVersionsCommand.class));

            mockMvc.perform(get("/internal/files/{fileId}/revisions", FILE_ID).param("userId", USER_ID.toString()))
                    .andExpect(status().isForbidden());

            then(recordFileAccessUseCase).shouldHaveNoInteractions();
        }
    }
}
