package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetAllFileVersionsCommand;
import com.moduDrive.file.application.port.in.usecase.GetAllFileVersionsUseCase;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
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

@WebMvcTest(GetAllFileVersionsController.class)
@Import(GlobalExceptionHandler.class)
class GetAllFileVersionsControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetAllFileVersionsUseCase getAllFileVersionsUseCase;

    private static final String FILE_ID = UUID.randomUUID().toString();
    private static final String USER_ID = UUID.randomUUID().toString();

    @Nested
    @DisplayName("GET /internal/files/{fileId}/versions/all")
    class GetAllFileVersions {

        @Test
        void returnsEveryVersion() throws Exception {
            FileVersion v = FileVersion.withId(new FileVersionId(UUID.randomUUID()),
                    new FileVersionFileId(UUID.fromString(FILE_ID)), new FileVersionFileSize(512L),
                    new FileVersionBlockCount(1), new FileVersionS3Path("s3://b/k"));
            given(getAllFileVersionsUseCase.getAllFileVersions(any(GetAllFileVersionsCommand.class)))
                    .willReturn(List.of(v));

            mockMvc.perform(get("/internal/files/{fileId}/versions/all", FILE_ID).param("userId", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].blockCount").value(1));
        }
    }
}
