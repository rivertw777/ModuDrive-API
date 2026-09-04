package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.ListPublicDirectoryCommand;
import com.moduDrive.file.application.port.in.usecase.ListPublicDirectoryUseCase;
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

@WebMvcTest(ListPublicDirectoryController.class)
@Import(GlobalExceptionHandler.class)
class ListPublicDirectoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ListPublicDirectoryUseCase listPublicDirectoryUseCase;

    private static final String TOKEN = UUID.randomUUID().toString();

    @Nested
    @DisplayName("토큰이 공개 폴더를 가리킬 때")
    class WhenTokenResolves {

        @Test
        void returnsNarrowChildEntriesWithoutAUserId() throws Exception {
            File child = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("a.txt"), new FilePath("/shared"), new FileOwnerId(UUID.randomUUID()),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            given(listPublicDirectoryUseCase.listPublicDirectory(any(ListPublicDirectoryCommand.class)))
                    .willReturn(List.of(child));

            mockMvc.perform(get("/api/v1/files/public/{fileId}/children", TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("a.txt"))
                    .andExpect(jsonPath("$.data[0].ownerId").doesNotExist());
        }
    }

    @Nested
    @DisplayName("토큰이 유효하지 않을 때")
    class WhenTokenIsRejected {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(listPublicDirectoryUseCase).listPublicDirectory(any(ListPublicDirectoryCommand.class));

            mockMvc.perform(get("/api/v1/files/public/{fileId}/children", "not-a-uuid"))
                    .andExpect(status().isNotFound());
        }
    }
}
