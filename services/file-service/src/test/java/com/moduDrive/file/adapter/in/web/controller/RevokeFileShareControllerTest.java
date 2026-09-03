package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.RevokeFileShareCommand;
import com.moduDrive.file.application.port.in.usecase.RevokeFileShareUseCase;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RevokeFileShareController.class)
@Import(GlobalExceptionHandler.class)
class RevokeFileShareControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RevokeFileShareUseCase revokeFileShareUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final UUID SHARE_ID = UUID.randomUUID();
    private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("소유자가 공유를 해제할 때")
    class WhenOwnerRevokes {

        @Test
        void returnsSuccess() throws Exception {
            mockMvc.perform(delete("/api/v1/files/{fileId}/shares/{shareId}", FILE_ID, SHARE_ID)
                            .header("X_USER_ID", OWNER_ID))
                    .andExpect(status().isOk());

            then(revokeFileShareUseCase).should().revokeFileShare(any(RevokeFileShareCommand.class));
        }
    }

    @Nested
    @DisplayName("호출자가 소유자가 아닐 때")
    class WhenCallerIsNotOwner {

        @Test
        void returnsForbidden() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_ACCESS_DENIED))
                    .given(revokeFileShareUseCase).revokeFileShare(any(RevokeFileShareCommand.class));

            mockMvc.perform(delete("/api/v1/files/{fileId}/shares/{shareId}", FILE_ID, SHARE_ID)
                            .header("X_USER_ID", OWNER_ID))
                    .andExpect(status().isForbidden());
        }
    }
}
