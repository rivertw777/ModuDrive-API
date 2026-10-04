package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.application.port.in.usecase.DownloadFileUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.io.OutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DownloadFileControllerTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    private MockMvc mockMvc;

    @Mock private DownloadFileUseCase downloadFileUseCase;
    @InjectMocks private DownloadFileController downloadFileController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(downloadFileController)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/storage/download/{fileId}")
    class DownloadFile {

        @Test
        void returnsFileBytesOnSuccess() throws Exception {
            byte[] data = "file content".getBytes();
            willAnswer(invocation -> {
                OutputStream out = invocation.getArgument(1);
                out.write(data);
                return null;
            }).given(downloadFileUseCase).downloadStream(any(), any());

            MvcResult asyncResult = mockMvc.perform(get("/api/v1/storage/download/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(asyncResult))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo(data));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/storage/view/{fileId}")
    class ViewFile {

        @Test
        void returnsImageContentTypeAndInlineDisposition() throws Exception {
            byte[] data = "image bytes".getBytes();
            given(downloadFileUseCase.download(any())).willReturn(data);

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .param("fileName", "photo.png"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline;")))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo(data));
        }

        @Test
        void returnsVideoContentTypeAndInlineDisposition() throws Exception {
            byte[] data = "video bytes".getBytes();
            given(downloadFileUseCase.download(any())).willReturn(data);

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .param("fileName", "clip.mp4"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline;")))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo(data));
        }

        @Test
        void fallsBackToOctetStreamForAnUnknownExtension() throws Exception {
            given(downloadFileUseCase.download(any())).willReturn("x".getBytes());

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .param("fileName", "archive.zip"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"));
        }

        @Test
        void fallsBackToOctetStreamForSvgToPreventInlineScriptExecution() throws Exception {
            given(downloadFileUseCase.download(any())).willReturn("<script>evil()</script>".getBytes());

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .param("fileName", "image.svg"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"));
        }

        @Test
        void setsNosniffToBlockMimeSniffingOfTheFallbackType() throws Exception {
            given(downloadFileUseCase.download(any())).willReturn("x".getBytes());

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .param("fileName", "photo.png"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }

        @Test
        void encodesANonAsciiFileNameInTheDisposition() throws Exception {
            given(downloadFileUseCase.download(any())).willReturn("x".getBytes());

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .param("fileName", "사진.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"__.jpg\"; filename*=UTF-8''%EC%82%AC%EC%A7%84.jpg"));
        }

        @Test
        void returnsBadRequestOnMissingFileName() throws Exception {
            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("스트림 토큰 파라미터는 더 이상 없다 — 신원은 게이트웨이가 넣은 X_USER_ID뿐")
        void requiresTheGatewayResolvedUser() throws Exception {
            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .param("fileName", "clip.mp4")
                            .param("streamToken", "tok-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returnsPartialContentForARangeRequest() throws Exception {
            byte[] data = "0123456789".getBytes();
            given(downloadFileUseCase.download(any())).willReturn(data);

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .header(HttpHeaders.RANGE, "bytes=2-4")
                            .param("fileName", "clip.mp4"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-4/10"))
                    .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo("234".getBytes()));
        }

        @Test
        void returnsTheFullBodyForAMultiRangeRequestInsteadOfSilentlyDroppingRanges() throws Exception {
            byte[] data = "0123456789".getBytes();
            given(downloadFileUseCase.download(any())).willReturn(data);

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .header(HttpHeaders.RANGE, "bytes=0-1,3-4")
                            .param("fileName", "clip.mp4"))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo(data));
        }
    }
}
