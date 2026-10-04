package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.exception.StorageExceptionCase;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
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
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicDownloadFileControllerTest {

    private MockMvc mockMvc;

    @Mock private PublicDownloadFileUseCase publicDownloadFileUseCase;
    @InjectMocks private PublicDownloadFileController publicDownloadFileController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(publicDownloadFileController)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Nested
    @DisplayName("GET /api/v1/storage/public/{fileId}/download")
    class PublicDownloadFile {

        @Test
        void returnsFileBytesWithoutRequiringAUserHeader() throws Exception {
            byte[] data = "public content".getBytes();
            willAnswer(invocation -> {
                OutputStream out = invocation.getArgument(1);
                out.write(data);
                return null;
            }).given(publicDownloadFileUseCase).downloadPublicStream(any(), any());

            MvcResult asyncResult = mockMvc.perform(get("/api/v1/storage/public/" + UUID.randomUUID() + "/download"))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(asyncResult))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo(data));
        }

        @Test
        void returnsTooManyRequestsAsCleanJsonWhenTheFileIsOverItsDownloadQuota() throws Exception {
            willAnswer(invocation -> { throw new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED); })
                    .given(publicDownloadFileUseCase).downloadPublicStream(any(), any());

            MvcResult asyncResult = mockMvc.perform(get("/api/v1/storage/public/" + UUID.randomUUID() + "/download"))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // The quota check runs inside the StreamingResponseBody lambda, before any byte is
            // written — so the response is still uncommitted and GlobalExceptionHandler can replace
            // the staged 200/attachment with a real 429 JSON body.
            mockMvc.perform(asyncDispatch(asyncResult))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("application/json")))
                    .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                    .andExpect(jsonPath("$.status").value("429 TOO_MANY_REQUESTS"));
        }

        @Test
        void marksTheResponseAsAnAttachment() throws Exception {
            willDoNothing().given(publicDownloadFileUseCase).downloadPublicStream(any(), any());

            MvcResult asyncResult = mockMvc.perform(get("/api/v1/storage/public/" + UUID.randomUUID() + "/download"))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            mockMvc.perform(asyncDispatch(asyncResult))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment;")));
        }

    }

    @Nested
    @DisplayName("GET /api/v1/storage/public/{fileId}/view")
    class ViewPublicFile {

        @Test
        void returnsInlineContentWithoutRequiringAUserHeader() throws Exception {
            byte[] data = "audio bytes".getBytes();
            given(publicDownloadFileUseCase.downloadPublic(any())).willReturn(data);

            mockMvc.perform(get("/api/v1/storage/public/" + UUID.randomUUID() + "/view")
                            .param("fileName", "song.mp3"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("inline;")))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo(data));
        }

        @Test
        void returnsPartialContentForARangeRequest() throws Exception {
            byte[] data = "0123456789".getBytes();
            given(publicDownloadFileUseCase.downloadPublic(any())).willReturn(data);

            mockMvc.perform(get("/api/v1/storage/public/" + UUID.randomUUID() + "/view")
                            .header(HttpHeaders.RANGE, "bytes=2-4")
                            .param("fileName", "song.mp3"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-4/10"))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo("234".getBytes()));
        }

        @Test
        void returnsTooManyRequestsWhenTheViewRouteIsOverItsDownloadQuota() throws Exception {
            given(publicDownloadFileUseCase.downloadPublic(any()))
                    .willThrow(new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED));

            mockMvc.perform(get("/api/v1/storage/public/" + UUID.randomUUID() + "/view")
                            .param("fileName", "song.mp3"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
        }
    }
}
