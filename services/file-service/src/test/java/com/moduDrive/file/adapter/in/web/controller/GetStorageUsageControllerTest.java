package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetStorageUsageCommand;
import com.moduDrive.file.application.port.in.usecase.GetStorageUsageUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GetStorageUsageController.class)
@Import(GlobalExceptionHandler.class)
class GetStorageUsageControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetStorageUsageUseCase getStorageUsageUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("GET /api/v1/files/usage")
    class GetStorageUsage {

        @Test
        void returnsUsedAndQuotaBytes() throws Exception {
            long quotaBytes = 21474836480L;
            given(getStorageUsageUseCase.getStorageUsage(any(GetStorageUsageCommand.class)))
                    .willReturn(new GetStorageUsageUseCase.StorageUsage(1024L, quotaBytes));

            mockMvc.perform(get("/api/v1/files/usage").header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.usedBytes").value(1024))
                    .andExpect(jsonPath("$.data.quotaBytes").value(quotaBytes));
        }
    }
}
