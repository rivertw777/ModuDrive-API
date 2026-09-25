package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GetSessionController.class)
@Import(GlobalExceptionHandler.class)
class GetSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("게이트웨이가 넣어 준 X_USER_ID가 있을 때")
    class WhenGatewayResolvedTheSession {

        @Test
        void returnsMemberId() throws Exception {
            mockMvc.perform(get("/api/v1/auth/session").header("X_USER_ID", "member-id"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.memberId").value("member-id"));
        }
    }
}
