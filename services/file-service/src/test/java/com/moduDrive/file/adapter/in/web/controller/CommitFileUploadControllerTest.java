package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.CommitFileUploadCommand;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase;
import com.moduDrive.file.application.port.in.usecase.CommitFileUploadUseCase.CommitResult;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.exception.FileExceptionCase;
import com.moduDrive.file.fixture.FileVersionTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.moduDrive.file.fixture.FileVersionTestFixture.HASH_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommitFileUploadController.class)
@Import(GlobalExceptionHandler.class)
class CommitFileUploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CommitFileUploadUseCase commitFileUploadUseCase;

    private static final UUID FILE_ID = UUID.randomUUID();
    private static final UUID UPLOAD_ID = UUID.randomUUID();
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    private static String body(String blocklistJson) {
        return "{\"uploadId\":\"" + UPLOAD_ID + "\",\"size\":10,\"blocklist\":" + blocklistJson + "}";
    }

    @Nested
    @DisplayName("빠진 블록이 있을 때")
    class WhenBlocksAreMissing {

        @Test
        void returnsNeedBlocksWithoutAVersion() throws Exception {
            given(commitFileUploadUseCase.commit(any())).willReturn(CommitResult.need(List.of(HASH_A)));

            mockMvc.perform(post("/api/v1/files/{fileId}/commit", FILE_ID)
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("[\"" + HASH_A + "\"]")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.needBlocks[0]").value(HASH_A))
                    .andExpect(jsonPath("$.data.versionId").doesNotExist());
        }
    }

    @Nested
    @DisplayName("버전이 만들어졌을 때")
    class WhenCommitted {

        @Test
        void returnsTheVersionIdAndPassesTheRequestThrough() throws Exception {
            FileVersion version = FileVersionTestFixture.aVersion(UUID.randomUUID(), FILE_ID, 10L);
            given(commitFileUploadUseCase.commit(any())).willReturn(CommitResult.committed(version));

            mockMvc.perform(post("/api/v1/files/{fileId}/commit", FILE_ID)
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("[\"" + HASH_A + "\"]")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.needBlocks").isEmpty())
                    .andExpect(jsonPath("$.data.versionId").value(version.getId().toString()));

            ArgumentCaptor<CommitFileUploadCommand> command = ArgumentCaptor.forClass(CommitFileUploadCommand.class);
            then(commitFileUploadUseCase).should().commit(command.capture());
            assertThat(command.getValue().getCallerId()).isEqualTo(UUID.fromString(USER_ID));
            assertThat(command.getValue().getUploadId().value()).isEqualTo(UPLOAD_ID);
            assertThat(command.getValue().getBlocklist().value()).containsExactly(HASH_A);
        }
    }

    @Nested
    @DisplayName("요청이 올바르지 않을 때")
    class WhenTheRequestIsInvalid {

        @Test
        @DisplayName("해시 형식이 틀리면 400")
        void rejectsAMalformedHash() throws Exception {
            mockMvc.perform(post("/api/v1/files/{fileId}/commit", FILE_ID)
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("[\"NOT-A-HASH\"]")))
                    .andExpect(status().isBadRequest());
            then(commitFileUploadUseCase).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("서비스가 거절하면 그 오류로 답한다")
        void answersTheServiceRejection() throws Exception {
            given(commitFileUploadUseCase.commit(any()))
                    .willThrow(new BusinessException(FileExceptionCase.INVALID_BLOCKLIST));

            mockMvc.perform(post("/api/v1/files/{fileId}/commit", FILE_ID)
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("[\"" + HASH_A + "\"]")))
                    .andExpect(status().isBadRequest());
        }
    }
}
