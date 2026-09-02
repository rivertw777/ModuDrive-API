package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetPublicDescendantVersionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicDescendantVersionsUseCase;
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

@WebMvcTest(GetPublicDescendantVersionsController.class)
@Import(GlobalExceptionHandler.class)
class GetPublicDescendantVersionsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetPublicDescendantVersionsUseCase getPublicDescendantVersionsUseCase;

    private static final String TOKEN = UUID.randomUUID().toString();
    private static final String ENTRY_ID = UUID.randomUUID().toString();

    @Nested
    @DisplayName("토큰과 엔트리가 유효할 때")
    class WhenResolves {

        @Test
        void returnsVersionList() throws Exception {
            FileVersion v = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(UUID.randomUUID()), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));
            given(getPublicDescendantVersionsUseCase
                    .getPublicDescendantVersions(any(GetPublicDescendantVersionsCommand.class)))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/internal/files/public/{token}/entry/{entryId}/revisions", TOKEN, ENTRY_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].s3Path").value("s3://b/k"));
        }
    }

    @Nested
    @DisplayName("엔트리가 폴더 밖이거나 토큰이 유효하지 않을 때")
    class WhenRejected {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(getPublicDescendantVersionsUseCase)
                    .getPublicDescendantVersions(any(GetPublicDescendantVersionsCommand.class));

            mockMvc.perform(get("/internal/files/public/{token}/entry/{entryId}/revisions", TOKEN, "not-a-uuid"))
                    .andExpect(status().isNotFound());
        }
    }
}
