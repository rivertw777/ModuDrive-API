package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.ListSharedWithMeCommand;
import com.moduDrive.file.application.port.in.usecase.ListSharedWithMeUseCase;
import com.moduDrive.file.application.port.in.usecase.FileView;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ListSharedWithMeController.class)
@Import(GlobalExceptionHandler.class)
class ListSharedWithMeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ListSharedWithMeUseCase listSharedWithMeUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("GET /api/v1/files/shared-with-me")
    class ListSharedWithMe {

        @Test
        void returnsSharedFileList() throws Exception {
            File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("shared.pdf"), new FilePath("/2/docs"),
                    new FileOwnerId(UUID.randomUUID()), null, null, FileStatus.UPLOADED,
                    new FileIsDirectory(false));
            given(listSharedWithMeUseCase.listSharedWithMe(any(ListSharedWithMeCommand.class)))
                    .willReturn(List.of(new FileView(
                            file, Role.EDITOR, "홍길동", "owner@modudrive.com",
                            LocalDateTime.of(2026, 9, 1, 9, 0), null, null)));

            mockMvc.perform(get("/api/v1/files/shared-with-me").header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("shared.pdf"))
                    .andExpect(jsonPath("$.data[0].sharedByName").value("홍길동"))
                    .andExpect(jsonPath("$.data[0].sharedByEmail").value("owner@modudrive.com"))
                    .andExpect(jsonPath("$.data[0].role").value("EDITOR"))
                    .andExpect(jsonPath("$.data[0].sharedAt").exists());
        }
    }
}
