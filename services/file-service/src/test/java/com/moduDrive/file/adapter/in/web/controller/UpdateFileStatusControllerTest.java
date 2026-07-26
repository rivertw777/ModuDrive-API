package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.UpdateFileStatusCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileStatusUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UpdateFileStatusController.class)
@Import(GlobalExceptionHandler.class)
class UpdateFileStatusControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UpdateFileStatusUseCase updateFileStatusUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final String REQUEST_JSON = """
            {"fileSize":1024,"blockCount":2,"s3Path":"s3://bucket/key"}
            """;

    private final File uploadedFile = File.withId(
            new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
            new FileName("report.pdf"), new FilePath("/1/docs"),
            new FileOwnerId(UUID.randomUUID()), new FileCurrentVersionId(UUID.randomUUID()),
            new FileSize(1024L), FileStatus.UPLOADED, new FileIsDirectory(false));

    @Nested
    @DisplayName("유효한 요청일 때")
    class WhenRequestIsValid {

        @Test
        void returnsUploadedFile() throws Exception {
            given(updateFileStatusUseCase.updateFileStatus(any(UpdateFileStatusCommand.class)))
                    .willReturn(uploadedFile);

            mockMvc.perform(put("/api/v1/files/{fileId}/uploaded", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("UPLOADED"));
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(updateFileStatusUseCase).updateFileStatus(any(UpdateFileStatusCommand.class));

            mockMvc.perform(put("/api/v1/files/{fileId}/uploaded", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(FileExceptionCase.FILE_NOT_FOUND.getMessage()));
        }
    }

    @Nested
    @DisplayName("필수 필드가 없을 때")
    class WhenRequiredFieldMissing {

        @Test
        void returnsBadRequest() throws Exception {
            mockMvc.perform(put("/api/v1/files/{fileId}/uploaded", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
