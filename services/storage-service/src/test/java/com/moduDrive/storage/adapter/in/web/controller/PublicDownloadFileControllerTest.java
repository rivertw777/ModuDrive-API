package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.exception.StorageExceptionCase;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import com.moduDrive.storage.application.port.in.usecase.RangeContent;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
            given(publicDownloadFileUseCase.viewPublicRange(any(), eq(2L), eq(4L)))
                    .willReturn(new RangeContent("234".getBytes(), 2, 10));

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
