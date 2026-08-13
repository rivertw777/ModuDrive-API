package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.UpdateFileScopeCommand;
import com.moduDrive.file.application.port.in.usecase.UpdateFileScopeUseCase;
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

@WebMvcTest(UpdateFileScopeController.class)
@Import(GlobalExceptionHandler.class)
class UpdateFileScopeControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UpdateFileScopeUseCase updateFileScopeUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String LINK_JSON = """
            {"scope":"LINK"}
            """;

    private static File file() {
        return File.withId(new FileId(FILE_ID), new FileNamespaceId(UUID.randomUUID()),
                new FileName("report.pdf"), new FilePath("/1"),
                new FileOwnerId(UUID.fromString(OWNER_ID)), null, null,
                FileStatus.UPLOADED, new FileIsDirectory(false));
    }

    @Nested
    @DisplayName("소유자가 LINK로 전환할 때")
    class WhenOwnerSwitchesToLink {

        @Test
        void returnsScopeWithLinkToken() throws Exception {
            UUID token = UUID.randomUUID();
            File linked = file();
            linked.enableLinkSharing(token);
            given(updateFileScopeUseCase.updateFileScope(any(UpdateFileScopeCommand.class))).willReturn(linked);

            mockMvc.perform(put("/api/v1/files/{fileId}/scope", FILE_ID)
                            .header("X_USER_ID", OWNER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LINK_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scope").value("LINK"))
                    .andExpect(jsonPath("$.data.linkToken").value(token.toString()));
        }

        @Test
        void returnsBadRequestWhenScopeIsMissing() throws Exception {
            mockMvc.perform(put("/api/v1/files/{fileId}/scope", FILE_ID)
                            .header("X_USER_ID", OWNER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("호출자가 소유자가 아닐 때")
    class WhenCallerIsNotOwner {

        @Test
        void returnsForbidden() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(updateFileScopeUseCase).updateFileScope(any(UpdateFileScopeCommand.class));

            mockMvc.perform(put("/api/v1/files/{fileId}/scope", FILE_ID)
                            .header("X_USER_ID", OWNER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LINK_JSON))
                    .andExpect(status().isForbidden());
        }
    }
}
