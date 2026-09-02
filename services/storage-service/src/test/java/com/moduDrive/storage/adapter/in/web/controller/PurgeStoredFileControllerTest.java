package com.moduDrive.storage.adapter.in.web.controller;

import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand;
import com.moduDrive.storage.application.port.in.usecase.PurgeStoredFileUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PurgeStoredFileController.class)
@Import(GlobalExceptionHandler.class)
class PurgeStoredFileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PurgeStoredFileUseCase purgeStoredFileUseCase;

    private static final String FILE_ID = UUID.randomUUID().toString();
    private static final String USER_ID = UUID.randomUUID().toString();

    @Nested
    @DisplayName("DELETE /internal/storage/{fileId}")
    class PurgeStoredFile {

        @Test
        void purgesStoredFile() throws Exception {
            mockMvc.perform(delete("/internal/storage/{fileId}", FILE_ID).param("userId", USER_ID))
                    .andExpect(status().isOk());

            then(purgeStoredFileUseCase).should().purgeStoredFile(any(PurgeStoredFileCommand.class));
        }
    }
}
