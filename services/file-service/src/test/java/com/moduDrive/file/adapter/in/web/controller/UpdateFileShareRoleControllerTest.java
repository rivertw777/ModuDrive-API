package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.in.command.UpdateFileShareRoleCommand;
import com.moduDrive.file.application.port.in.usecase.RecordFileAccessUseCase;
import com.moduDrive.file.application.port.in.usecase.UpdateFileShareRoleUseCase;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.*;
import com.moduDrive.file.domain.model.Role;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UpdateFileShareRoleController.class)
@Import(GlobalExceptionHandler.class)
class UpdateFileShareRoleControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UpdateFileShareRoleUseCase updateFileShareRoleUseCase;
    @MockitoBean private RecordFileAccessUseCase recordFileAccessUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final UUID SHARE_ID = UUID.randomUUID();
    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_JSON = """
            {"role":"EDITOR"}
            """;

    @Nested
    @DisplayName("소유자가 권한을 변경할 때")
    class WhenOwnerUpdatesRole {

        @Test
        void returnsUpdatedShare() throws Exception {
            FileShare share = FileShare.withId(new FileShareId(SHARE_ID), new FileShareFileId(FILE_ID),
                    new FileShareOwnerId(UUID.fromString(OWNER_ID)),
                    new FileShareSharedWithUserId(UUID.randomUUID()), new FileShareRole(Role.EDITOR));
            given(updateFileShareRoleUseCase.updateFileShareRole(any(UpdateFileShareRoleCommand.class)))
                    .willReturn(share);

            mockMvc.perform(patch("/api/v1/files/{fileId}/shares/{shareId}", FILE_ID, SHARE_ID)
                            .header("X_USER_ID", OWNER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("EDITOR"));

            then(recordFileAccessUseCase).should().recordAccess(any(RecordFileAccessCommand.class));
        }

        @Test
        void returnsBadRequestWhenRoleIsMissing() throws Exception {
            mockMvc.perform(patch("/api/v1/files/{fileId}/shares/{shareId}", FILE_ID, SHARE_ID)
                            .header("X_USER_ID", OWNER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("공유가 해당 파일에 없을 때")
    class WhenShareNotFound {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_SHARE_NOT_FOUND))
                    .given(updateFileShareRoleUseCase).updateFileShareRole(any(UpdateFileShareRoleCommand.class));

            mockMvc.perform(patch("/api/v1/files/{fileId}/shares/{shareId}", FILE_ID, SHARE_ID)
                            .header("X_USER_ID", OWNER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isNotFound());

            then(recordFileAccessUseCase).shouldHaveNoInteractions();
        }
    }
}
