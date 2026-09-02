package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.EmptyTrashCommand;
import com.moduDrive.file.application.port.in.usecase.EmptyTrashUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmptyTrashController.class)
@Import(GlobalExceptionHandler.class)
class EmptyTrashControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private EmptyTrashUseCase emptyTrashUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Nested
    @DisplayName("DELETE /api/v1/files/trash")
    class EmptyTrash {

        @Test
        void emptiesTrash() throws Exception {
            mockMvc.perform(delete("/api/v1/files/trash").header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk());

            then(emptyTrashUseCase).should().emptyTrash(any(EmptyTrashCommand.class));
        }
    }
}
