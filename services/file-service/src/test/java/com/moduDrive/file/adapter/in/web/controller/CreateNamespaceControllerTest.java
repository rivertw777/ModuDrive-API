package com.moduDrive.file.adapter.in.web.controller;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.file.application.port.in.command.CreateNamespaceCommand;
import com.moduDrive.file.application.port.in.usecase.CreateNamespaceUseCase;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceQuotaBytes;
import com.moduDrive.file.domain.model.Namespace.NamespaceRootPath;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
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
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreateNamespaceController.class)
@Import(GlobalExceptionHandler.class)
class CreateNamespaceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CreateNamespaceUseCase createNamespaceUseCase;

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_JSON = """
            {"userId":"%s"}
            """.formatted(USER_ID);

    @Nested
    @DisplayName("유효한 요청일 때")
    class WhenRequestIsValid {

        @Test
        void returnsCreatedNamespace() throws Exception {
            UUID id = UUID.randomUUID();
            given(createNamespaceUseCase.createNamespace(any(CreateNamespaceCommand.class)))
                    .willReturn(Namespace.withId(
                            new NamespaceId(id),
                            new NamespaceUserId(UUID.fromString(USER_ID)),
                            new NamespaceRootPath("/1"),
                            new NamespaceQuotaBytes(21474836480L)
                    ));

            mockMvc.perform(post("/internal/v1/namespaces")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(USER_ID))
                    .andExpect(jsonPath("$.data.rootPath").value("/1"));
        }
    }

    @Nested
    @DisplayName("userId가 없을 때")
    class WhenUserIdIsMissing {

        @Test
        void returnsBadRequest() throws Exception {
            mockMvc.perform(post("/internal/v1/namespaces")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("이미 존재하는 네임스페이스일 때")
    class WhenNamespaceAlreadyExists {

        @Test
        void returnsBadRequestWithMessage() throws Exception {
            willThrow(new BusinessException(FileExceptionCase.NAMESPACE_ALREADY_EXISTS))
                    .given(createNamespaceUseCase).createNamespace(any(CreateNamespaceCommand.class));

            mockMvc.perform(post("/internal/v1/namespaces")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(FileExceptionCase.NAMESPACE_ALREADY_EXISTS.getMessage()));
        }
    }
}
