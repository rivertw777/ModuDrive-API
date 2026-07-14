package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.ShareFileCommand;
import com.moduDrive.file.application.port.in.usecase.ShareFileUseCase;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.Permission;
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

@WebMvcTest(ShareFileController.class)
@Import(GlobalExceptionHandler.class)
class ShareFileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ShareFileUseCase shareFileUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final String REQUEST_JSON = """
            {"ownerId":1,"sharedWithUserId":2,"permission":"READ"}
            """;

    @Nested
    @DisplayName("유효한 요청일 때")
    class WhenRequestIsValid {

        @Test
        void returnsFileShare() throws Exception {
            FileShare share = FileShare.withId(
                    new FileShareId(UUID.randomUUID()), new FileShareFileId(FILE_ID),
                    new FileShareOwnerId(1L), new FileShareSharedWithUserId(2L),
                    new FileSharePermission(Permission.READ));
            given(shareFileUseCase.shareFile(any(ShareFileCommand.class))).willReturn(share);

            mockMvc.perform(post("/api/v1/files/{fileId}/share", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.permission").value("READ"));
        }
    }

    @Nested
    @DisplayName("이미 공유된 파일일 때")
    class WhenAlreadyShared {

        @Test
        void returnsBadRequest() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS))
                    .given(shareFileUseCase).shareFile(any(ShareFileCommand.class));

            mockMvc.perform(post("/api/v1/files/{fileId}/share", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS.getMessage()));
        }
    }
}
