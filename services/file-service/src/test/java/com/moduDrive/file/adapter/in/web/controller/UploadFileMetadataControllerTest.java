package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.UploadFileMetadataCommand;
import com.moduDrive.file.application.port.in.usecase.UploadFileMetadataUseCase;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadFileMetadataController.class)
@Import(GlobalExceptionHandler.class)
class UploadFileMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UploadFileMetadataUseCase uploadFileMetadataUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_JSON = """
            {"name":"report.pdf","path":"/1/docs","directory":false}
            """;

    private final File pendingFile = File.withId(
            new FileId(UUID.randomUUID()),
            new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"),
            new FilePath("/1/docs"),
            new FileOwnerId(UUID.fromString(USER_ID)),
            null, null,
            FileStatus.PENDING,
            new FileIsDirectory(false));

    @Nested
    @DisplayName("유효한 요청일 때")
    class WhenRequestIsValid {

        @Test
        void returnsPendingFile() throws Exception {
            given(uploadFileMetadataUseCase.uploadFileMetadata(any(UploadFileMetadataCommand.class)))
                    .willReturn(pendingFile);

            mockMvc.perform(post("/api/v1/files/metadata")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.name").value("report.pdf"));
        }
    }

    @Nested
    @DisplayName("필수 필드가 없을 때")
    class WhenRequiredFieldMissing {

        @Test
        void returnsBadRequest() throws Exception {
            mockMvc.perform(post("/api/v1/files/metadata")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("네임스페이스가 없을 때")
    class WhenNamespaceNotFound {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.NAMESPACE_NOT_FOUND))
                    .given(uploadFileMetadataUseCase).uploadFileMetadata(any(UploadFileMetadataCommand.class));

            mockMvc.perform(post("/api/v1/files/metadata")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message")
                            .value(FileExceptionCase.NAMESPACE_NOT_FOUND.getMessage()));
        }
    }
}
