package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetFilesByCategoryCommand;
import com.moduDrive.file.application.port.in.usecase.GetFilesByCategoryUseCase;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
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

@WebMvcTest(GetFilesByCategoryController.class)
@Import(GlobalExceptionHandler.class)
class GetFilesByCategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetFilesByCategoryUseCase getFilesByCategoryUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("GET /api/v1/files/category")
    class GetFilesByCategory {

        @Test
        void returnsMatchingFileList() throws Exception {
            File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("photo.png"), new FilePath("/1"),
                    new FileOwnerId(UUID.fromString(USER_ID)), null, null, FileStatus.UPLOADED,
                    new FileIsDirectory(false));
            given(getFilesByCategoryUseCase.getFilesByCategory(any(GetFilesByCategoryCommand.class)))
                    .willReturn(List.of(file));

            mockMvc.perform(get("/api/v1/files/category")
                            .header("X_USER_ID", USER_ID)
                            .param("type", "IMAGE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("photo.png"));
        }

        @Test
        void invalidTypeIsRejected() throws Exception {
            // GlobalExceptionHandler now handles MethodArgumentTypeMismatchException
            // (enum conversion failures included) with a 400.
            mockMvc.perform(get("/api/v1/files/category")
                            .header("X_USER_ID", USER_ID)
                            .param("type", "NOT_A_CATEGORY"))
                    .andExpect(status().isBadRequest());
        }
    }
}
