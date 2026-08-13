package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetFileRevisionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetFileRevisionsUseCase;
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

@WebMvcTest(GetFileRevisionsController.class)
@Import(GlobalExceptionHandler.class)
class GetFileRevisionsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetFileRevisionsUseCase getFileRevisionsUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("리비전이 존재할 때")
    class WhenRevisionsExist {

        @Test
        void returnsRevisionList() throws Exception {
            FileVersion v = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(FILE_ID), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));
            given(getFileRevisionsUseCase.getFileRevisions(any(GetFileRevisionsCommand.class)))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/api/v1/files/{fileId}/revisions", FILE_ID)
                            .header("X_USER_ID", USER_ID))
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
                    .given(getFileRevisionsUseCase).getFileRevisions(any(GetFileRevisionsCommand.class));

            mockMvc.perform(get("/api/v1/files/{fileId}/revisions", FILE_ID)
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isNotFound());
        }
    }
}
