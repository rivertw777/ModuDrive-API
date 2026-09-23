package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.command.PrepareArchiveCommand;
import com.moduDrive.storage.application.port.in.usecase.OpenArchiveUseCase;
import com.moduDrive.storage.application.port.in.usecase.OpenArchiveUseCase.Archive;
import com.moduDrive.storage.application.port.in.usecase.PrepareArchiveUseCase;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ArchiveControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FILE_ID = UUID.randomUUID();

    private MockMvc mockMvc;

    @Mock private PrepareArchiveUseCase prepareArchiveUseCase;
    @Mock private OpenArchiveUseCase openArchiveUseCase;
    @InjectMocks private ArchiveController archiveController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(archiveController)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/storage/archive")
    class Prepare {

        @Test
        void returnsATokenForTheSignedInCaller() throws Exception {
            given(prepareArchiveUseCase.prepare(new PrepareArchiveCommand(USER_ID, null, List.of(FILE_ID)))).willReturn("tok");

            mockMvc.perform(post("/api/v1/storage/archive")
                            .header("X_USER_ID", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileIds\":[\"" + FILE_ID + "\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("tok"));
        }

        @Test
        void rejectsAnEmptySelection() throws Exception {
            mockMvc.perform(post("/api/v1/storage/archive")
                            .header("X_USER_ID", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileIds\":[]}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/storage/public/archive")
    class PreparePublic {

        @Test
        void returnsATokenWithoutAUserHeader() throws Exception {
            given(prepareArchiveUseCase.prepare(new PrepareArchiveCommand(null, "k", List.of(FILE_ID)))).willReturn("tok");

            mockMvc.perform(post("/api/v1/storage/public/archive")
                            .param("key", "k")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileIds\":[\"" + FILE_ID + "\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").value("tok"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/storage/public/archive/{token}")
    class Download {

        @Test
        void streamsTheZipAsAnAttachmentWithAUtf8Name() throws Exception {
            given(openArchiveUseCase.open("tok")).willReturn(new Archive("사진.zip", out -> out.write("zip".getBytes())));

            MvcResult async = mockMvc.perform(get("/api/v1/storage/public/archive/tok"))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(async))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/zip"))
                    .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''%EC%82%AC%EC%A7%84.zip")))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("zip"));
        }

        @Test
        void answersNotFoundForAnExpiredToken() throws Exception {
            given(openArchiveUseCase.open("old")).willThrow(new BusinessException(StorageExceptionCase.ARCHIVE_TOKEN_INVALID));

            mockMvc.perform(get("/api/v1/storage/public/archive/old"))
                    .andExpect(status().isNotFound());
        }
    }
}
