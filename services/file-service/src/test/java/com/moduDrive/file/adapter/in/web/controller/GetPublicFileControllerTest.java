package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.GetPublicFileCommand;
import com.moduDrive.file.application.port.in.usecase.GetPublicFileUseCase;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.*;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.exception.FileExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GetPublicFileController.class)
@Import(GlobalExceptionHandler.class)
class GetPublicFileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetPublicFileUseCase getPublicFileUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final UUID KEY = UUID.randomUUID();

    @Nested
    @DisplayName("공개 링크가 유효할 때")
    class WhenLinkIsValid {

        @Test
        void returnsReadOnlyViewWithoutAuthenticationAndWithoutInternalIds() throws Exception {
            File file = File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                    new FileName("report.pdf"), new FilePath("/1"), new FileOwnerId(UUID.randomUUID()),
                    null, null, FileStatus.UPLOADED, new FileIsDirectory(false));
            given(getPublicFileUseCase.getPublicFile(any(GetPublicFileCommand.class))).willReturn(file);

            mockMvc.perform(get("/api/v1/files/public/{fileId}", FILE_ID).param("key", KEY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("report.pdf"))
                    .andExpect(jsonPath("$.data.ownerId").doesNotExist())
                    .andExpect(jsonPath("$.data.namespaceId").doesNotExist())
                    .andExpect(jsonPath("$.data.path").doesNotExist());
        }

        @Test
        void bindsTheFileIdFromThePathAndTheKeyFromTheQueryStringIntoTheCommand() throws Exception {
            given(getPublicFileUseCase.getPublicFile(any(GetPublicFileCommand.class)))
                    .willReturn(File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                            new FileName("f.txt"), new FilePath("/1"), new FileOwnerId(UUID.randomUUID()),
                            null, null, FileStatus.UPLOADED, new FileIsDirectory(false)));

            mockMvc.perform(get("/api/v1/files/public/{fileId}", FILE_ID).param("key", KEY.toString()))
                    .andExpect(status().isOk());

            ArgumentCaptor<GetPublicFileCommand> captor = ArgumentCaptor.forClass(GetPublicFileCommand.class);
            then(getPublicFileUseCase).should().getPublicFile(captor.capture());
            assertThat(captor.getValue().getFileId()).isEqualTo(FILE_ID.toString());
            assertThat(captor.getValue().getKey()).isEqualTo(KEY.toString());
        }
    }

    @Nested
    @DisplayName("링크가 유효하지 않거나 공개가 해제됐을 때")
    class WhenLinkIsNotUsable {

        @Test
        void returnsNotFound() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(getPublicFileUseCase).getPublicFile(any(GetPublicFileCommand.class));

            mockMvc.perform(get("/api/v1/files/public/{fileId}", FILE_ID).param("key", KEY.toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void aRequestWithNoKeyStillReachesTheUseCaseWithANullKey() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_NOT_FOUND))
                    .given(getPublicFileUseCase).getPublicFile(any(GetPublicFileCommand.class));

            mockMvc.perform(get("/api/v1/files/public/{fileId}", FILE_ID))
                    .andExpect(status().isNotFound());

            ArgumentCaptor<GetPublicFileCommand> captor = ArgumentCaptor.forClass(GetPublicFileCommand.class);
            then(getPublicFileUseCase).should().getPublicFile(captor.capture());
            assertThat(captor.getValue().getKey()).isNull();
        }
    }
}
