package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.UpdateFileFavoriteCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileFavoriteUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UpdateFileFavoriteController.class)
@Import(GlobalExceptionHandler.class)
class UpdateFileFavoriteControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UpdateFileFavoriteUseCase updateFileFavoriteUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("파일이 존재할 때")
    class WhenFileExists {

        @Test
        void returnsUpdatedFile() throws Exception {
            File favorited = File.withId(new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/1/docs"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            favorited.markFavorite(true);
            given(updateFileFavoriteUseCase.updateFavorite(any(UpdateFileFavoriteCommand.class))).willReturn(favorited);

            mockMvc.perform(patch("/api/v1/files/{fileId}/favorite", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"favorite\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.favorite").value(true));
        }
    }

    @Nested
    @DisplayName("파일이 없을 때")
    class WhenFileNotFound {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(updateFileFavoriteUseCase).updateFavorite(any(UpdateFileFavoriteCommand.class));

            mockMvc.perform(patch("/api/v1/files/{fileId}/favorite", FILE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"favorite\":true}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(FileExceptionCase.FILE_NOT_FOUND.getMessage()));
        }
    }
}
