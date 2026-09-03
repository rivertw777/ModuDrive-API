package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.ListSharedDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.ListSharedDirectoryUseCase;
import com.moduDrive.file.application.port.in.usecase.FileView;
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

@WebMvcTest(ListSharedDirectoryController.class)
@Import(GlobalExceptionHandler.class)
class ListSharedDirectoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ListSharedDirectoryUseCase listSharedDirectoryUseCase;

    private static final String CALLER_ID = "11111111-1111-1111-1111-111111111111";
    private static final UUID DIR_ID = UUID.randomUUID();

    @Nested
    @DisplayName("GET /api/v1/files/{fileId}/children")
    class ListChildren {

        @Test
        void returnsChildEntries() throws Exception {
            File child = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("a.txt"), new FilePath("/shared"), new FileOwnerId(UUID.randomUUID()),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            given(listSharedDirectoryUseCase.listSharedDirectory(any(ListSharedDirectoryCommand.class)))
                    .willReturn(List.of(FileView.owned(child)));

            mockMvc.perform(get("/api/v1/files/{fileId}/children", DIR_ID).header("X_USER_ID", CALLER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("a.txt"));
        }

        @Test
        void returnsForbiddenWhenCallerHasNoAccess() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(listSharedDirectoryUseCase).listSharedDirectory(any(ListSharedDirectoryCommand.class));

            mockMvc.perform(get("/api/v1/files/{fileId}/children", DIR_ID).header("X_USER_ID", CALLER_ID))
                    .andExpect(status().isForbidden());
        }
    }
}
