package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.UploadBatchCommand;
import com.moduDrive.file.application.port.in.command.UploadBatchCommand.ConflictResolution;
import com.moduDrive.file.application.port.in.usecase.UploadBatchUseCase;
import com.moduDrive.file.application.port.in.usecase.UploadBatchUseCase.UploadedItem;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadBatchController.class)
@Import(GlobalExceptionHandler.class)
class UploadBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private UploadBatchUseCase uploadBatchUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_JSON = """
            {"path":"/","items":[
              {"relativePath":"사진","directory":true},
              {"relativePath":"사진/a.jpg","directory":false,"size":1200},
              {"relativePath":"보고서.pdf","directory":false,"size":3000}
            ],"resolutions":{"보고서.pdf":"REPLACE"}}
            """;

    private static File entry(String name, String path, boolean directory, FileStatus status) {
        return File.withId(new FileId(UUID.randomUUID()), new FileNamespaceId(UUID.randomUUID()),
                new FileName(name), new FilePath(path), new FileOwnerId(UUID.fromString(USER_ID)),
                null, null, status, new FileIsDirectory(directory));
    }

    private void perform(String json, org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        mockMvc.perform(post("/api/v1/files/batch")
                        .header("X_USER_ID", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(expected);
    }

    @Nested
    @DisplayName("유효한 요청일 때")
    class WhenRequestIsValid {

        private final File folder = entry("사진 (1)", "/", true, FileStatus.UPLOADED);
        private final File photo = entry("a.jpg", "/사진 (1)", false, FileStatus.PENDING);
        private final File report = entry("보고서.pdf", "/", false, FileStatus.PENDING);

        @Test
        @DisplayName("만들어진 항목을 요청 경로와 실제 위치로 돌려준다")
        void returnsEveryCreatedItem() throws Exception {
            given(uploadBatchUseCase.uploadBatch(any(UploadBatchCommand.class))).willReturn(List.of(
                    new UploadedItem("사진", folder, false),
                    new UploadedItem("사진/a.jpg", photo, false),
                    new UploadedItem("보고서.pdf", report, true)));

            mockMvc.perform(post("/api/v1/files/batch")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(3))
                    .andExpect(jsonPath("$.data.items[0].relativePath").value("사진"))
                    .andExpect(jsonPath("$.data.items[0].name").value("사진 (1)"))
                    .andExpect(jsonPath("$.data.items[0].directory").value(true))
                    .andExpect(jsonPath("$.data.items[1].path").value("/사진 (1)"))
                    .andExpect(jsonPath("$.data.items[1].fileId").value(photo.getId().toString()))
                    .andExpect(jsonPath("$.data.items[2].replaced").value(true));
        }

        @Test
        @DisplayName("요청을 커맨드로 옮긴다")
        void mapsTheRequestToACommand() throws Exception {
            given(uploadBatchUseCase.uploadBatch(any(UploadBatchCommand.class))).willReturn(List.of(
                    new UploadedItem("사진", folder, false),
                    new UploadedItem("사진/a.jpg", photo, false),
                    new UploadedItem("보고서.pdf", report, true)));

            perform(REQUEST_JSON, status().isOk());

            ArgumentCaptor<UploadBatchCommand> captor = ArgumentCaptor.forClass(UploadBatchCommand.class);
            then(uploadBatchUseCase).should().uploadBatch(captor.capture());
            UploadBatchCommand command = captor.getValue();
            assertThat(command.getUserId()).isEqualTo(UUID.fromString(USER_ID));
            assertThat(command.getTargetPath().value()).isEqualTo("/");
            assertThat(command.getItems()).containsExactly(
                    new UploadBatchCommand.Item("사진", true, null),
                    new UploadBatchCommand.Item("사진/a.jpg", false, 1200L),
                    new UploadBatchCommand.Item("보고서.pdf", false, 3000L));
            assertThat(command.getResolutions()).isEqualTo(Map.of("보고서.pdf", ConflictResolution.REPLACE));
        }
    }

    @Nested
    @DisplayName("파일 이름 충돌에 대한 선택이 없을 때")
    class WhenConflictsAreUnresolved {

        @Test
        @DisplayName("409와 충돌 이름 목록을 돌려준다")
        void returnsConflictWithNames() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.FILE_BATCH_CONFLICT, Map.of("conflicts", List.of("보고서.pdf"))))
                    .given(uploadBatchUseCase).uploadBatch(any(UploadBatchCommand.class));

            mockMvc.perform(post("/api/v1/files/batch")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.data.conflicts[0]").value("보고서.pdf"));
        }
    }

    @Nested
    @DisplayName("요청 형식이 잘못됐을 때")
    class WhenRequestIsMalformed {

        @Test
        void rejectsEmptyItems() throws Exception {
            perform("{\"path\":\"/\",\"items\":[]}", status().isBadRequest());
        }

        @Test
        @DisplayName("항목이 5,000개를 넘으면 거부한다")
        void rejectsMoreThanFiveThousandItems() throws Exception {
            StringBuilder items = new StringBuilder();
            for (int i = 0; i <= 5000; i++) {
                items.append(i == 0 ? "" : ",").append("{\"relativePath\":\"f").append(i).append("\",\"directory\":true}");
            }

            perform("{\"path\":\"/\",\"items\":[" + items + "]}", status().isBadRequest());
            then(uploadBatchUseCase).shouldHaveNoInteractions();
        }

        @Test
        void rejectsNegativeSize() throws Exception {
            perform("{\"path\":\"/\",\"items\":[{\"relativePath\":\"a\",\"directory\":false,\"size\":-1}]}",
                    status().isBadRequest());
        }

        @Test
        @DisplayName("상대 경로가 255자를 넘으면 서비스까지 가지 않고 거부한다")
        void rejectsAnOverlongRelativePath() throws Exception {
            String deep = "a/".repeat(128) + "b"; // 257자, 129단계
            perform("{\"path\":\"/\",\"items\":[{\"relativePath\":\"" + deep + "\",\"directory\":true}]}",
                    status().isBadRequest());
            then(uploadBatchUseCase).shouldHaveNoInteractions();
        }

        @Test
        void rejectsANullResolution() throws Exception {
            perform("{\"path\":\"/\",\"items\":[{\"relativePath\":\"a\",\"directory\":false,\"size\":1}],"
                    + "\"resolutions\":{\"a\":null}}", status().isBadRequest());
            then(uploadBatchUseCase).shouldHaveNoInteractions();
        }

        @Test
        void rejectsUnknownResolution() throws Exception {
            perform("{\"path\":\"/\",\"items\":[{\"relativePath\":\"a\",\"directory\":false,\"size\":1}],"
                    + "\"resolutions\":{\"a\":\"MERGE\"}}", status().isBadRequest());
        }
    }
}
