package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetPublicFileRevisionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicFileRevisionsUseCase;
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

@WebMvcTest(GetPublicFileRevisionsController.class)
@Import(GlobalExceptionHandler.class)
class GetPublicFileRevisionsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetPublicFileRevisionsUseCase getPublicFileRevisionsUseCase;

    private static final String TOKEN = UUID.randomUUID().toString();

    @Nested
    @DisplayName("토큰이 공개 파일을 가리킬 때")
    class WhenTokenResolves {

        @Test
        void returnsVersionListWithoutRequiringAUserId() throws Exception {
            FileVersion v = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(UUID.randomUUID()), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));
            given(getPublicFileRevisionsUseCase.getPublicFileRevisions(any(GetPublicFileRevisionsCommand.class)))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/internal/files/public/{fileId}/revisions", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].s3Path").value("s3://b/k"));
        }
    }

    @Nested
    @DisplayName("토큰이 유효하지 않을 때")
    class WhenTokenIsRejected {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(getPublicFileRevisionsUseCase)
                    .getPublicFileRevisions(any(GetPublicFileRevisionsCommand.class));

            mockMvc.perform(get("/internal/files/public/{fileId}/revisions", TOKEN))
                    .andExpect(status().isNotFound());
        }

        @Test
        void returnsNotFoundForAMalformedTokenRatherThanAConversionError() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(getPublicFileRevisionsUseCase)
                    .getPublicFileRevisions(any(GetPublicFileRevisionsCommand.class));

            mockMvc.perform(get("/internal/files/public/{fileId}/revisions", "not-a-uuid"))
                    .andExpect(status().isNotFound());
        }
    }
}
