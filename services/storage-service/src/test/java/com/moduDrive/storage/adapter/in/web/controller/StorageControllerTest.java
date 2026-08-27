package com.moduDrive.storage.adapter.in.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.core.web.GlobalExceptionHandler;
import com.moduDrive.storage.exception.StorageExceptionCase;
import com.moduDrive.storage.application.port.in.usecase.CompleteResumableUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.DownloadFileUseCase;
import com.moduDrive.storage.application.port.in.usecase.InitResumableUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.IssueStreamTokenUseCase;
import com.moduDrive.storage.application.port.in.usecase.PublicDownloadFileUseCase;
import com.moduDrive.storage.application.port.in.usecase.ResolveViewIdentityUseCase;
import com.moduDrive.storage.application.port.in.usecase.SimpleUploadUseCase;
import com.moduDrive.storage.application.port.in.usecase.UploadChunkUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    private static final String USER_ID = UUID.randomUUID().toString();

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private SimpleUploadUseCase simpleUploadUseCase;
    @Mock private InitResumableUploadUseCase initResumableUploadUseCase;
    @Mock private UploadChunkUseCase uploadChunkUseCase;
    @Mock private CompleteResumableUploadUseCase completeResumableUploadUseCase;
    @Mock private DownloadFileUseCase downloadFileUseCase;
    @Mock private PublicDownloadFileUseCase publicDownloadFileUseCase;
    @Mock private IssueStreamTokenUseCase issueStreamTokenUseCase;
    @Mock private ResolveViewIdentityUseCase resolveViewIdentityUseCase;
    @InjectMocks private StorageController storageController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(storageController)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Nested
    @DisplayName("POST /api/v1/storage/upload")
    class SimpleUpload {

        @Test
        void returnsOkOnSuccess() throws Exception {
            willDoNothing().given(simpleUploadUseCase).simpleUpload(any());
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "hello".getBytes());

            mockMvc.perform(multipart("/api/v1/storage/upload")
                            .file(file)
                            .header("X_USER_ID", USER_ID)
                            .param("fileId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));
        }

        @Test
        void returnsBadRequestOnMissingFileId() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "hello".getBytes());

            mockMvc.perform(multipart("/api/v1/storage/upload")
                            .file(file)
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/storage/upload/resumable")
    class InitResumableUpload {

        @Test
        void returnsSessionIdOnSuccess() throws Exception {
            UUID sessionId = UUID.randomUUID();
            given(initResumableUploadUseCase.initResumableUpload(any())).willReturn(sessionId);

            mockMvc.perform(post("/api/v1/storage/upload/resumable")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":\"" + UUID.randomUUID() + "\",\"totalChunks\":5,\"fileSize\":1000000}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.sessionId").value(sessionId.toString()));
        }

        @Test
        void returnsBadRequestWhenFileIdBlank() throws Exception {
            mockMvc.perform(post("/api/v1/storage/upload/resumable")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":\"\",\"totalChunks\":3,\"fileSize\":1000000}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returnsBadRequestWhenTotalChunksZero() throws Exception {
            mockMvc.perform(post("/api/v1/storage/upload/resumable")
                            .header("X_USER_ID", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fileId\":\"" + UUID.randomUUID() + "\",\"totalChunks\":0,\"fileSize\":1000000}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/storage/upload/resumable/{sessionId}")
    class UploadChunk {

        @Test
        void returnsOkOnSuccess() throws Exception {
            willDoNothing().given(uploadChunkUseCase).uploadChunk(any());
            MockMultipartFile chunk = new MockMultipartFile(
                    "chunk", "chunk0.bin", "application/octet-stream", "data".getBytes());

            mockMvc.perform(multipart(PUT, "/api/v1/storage/upload/resumable/" + UUID.randomUUID())
                            .file(chunk)
                            .header("X_USER_ID", USER_ID)
                            .param("chunkIndex", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));
        }

        @Test
        void returnsBadRequestOnMissingChunkIndex() throws Exception {
            MockMultipartFile chunk = new MockMultipartFile(
                    "chunk", "chunk0.bin", "application/octet-stream", "data".getBytes());

            mockMvc.perform(multipart(PUT, "/api/v1/storage/upload/resumable/" + UUID.randomUUID())
                            .file(chunk)
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/storage/upload/resumable/{sessionId}/complete")
    class CompleteResumableUpload {

        @Test
        void returnsOkOnSuccess() throws Exception {
            willDoNothing().given(completeResumableUploadUseCase).completeResumableUpload(any());

            mockMvc.perform(post("/api/v1/storage/upload/resumable/" + UUID.randomUUID() + "/complete")
                            .header("X_USER_ID", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("success"));
        }
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
    @DisplayName("GET /api/v1/storage/public/{token}/download")
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
    @DisplayName("GET /api/v1/storage/view/{fileId}")
    class ViewFile {

        @Test
        void returnsImageContentTypeAndInlineDisposition() throws Exception {
            byte[] data = "image bytes".getBytes();
            given(resolveViewIdentityUseCase.resolve(any())).willReturn(UUID.fromString(USER_ID));
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
            given(resolveViewIdentityUseCase.resolve(any())).willReturn(UUID.fromString(USER_ID));
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
            given(resolveViewIdentityUseCase.resolve(any())).willReturn(UUID.fromString(USER_ID));
            given(downloadFileUseCase.download(any())).willReturn("x".getBytes());

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .param("fileName", "archive.zip"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"));
        }

        @Test
        void fallsBackToOctetStreamForSvgToPreventInlineScriptExecution() throws Exception {
            given(resolveViewIdentityUseCase.resolve(any())).willReturn(UUID.fromString(USER_ID));
            given(downloadFileUseCase.download(any())).willReturn("<script>evil()</script>".getBytes());

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .param("fileName", "image.svg"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"));
        }

        @Test
        void setsNosniffToBlockMimeSniffingOfTheFallbackType() throws Exception {
            given(resolveViewIdentityUseCase.resolve(any())).willReturn(UUID.fromString(USER_ID));
            given(downloadFileUseCase.download(any())).willReturn("x".getBytes());

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .header("X_USER_ID", USER_ID)
                            .param("fileName", "photo.png"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }

        @Test
        void encodesANonAsciiFileNameInTheDisposition() throws Exception {
            given(resolveViewIdentityUseCase.resolve(any())).willReturn(UUID.fromString(USER_ID));
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
        void returnsUnauthorizedWithNeitherAHeaderNorAStreamToken() throws Exception {
            given(resolveViewIdentityUseCase.resolve(any()))
                    .willThrow(new BusinessException(StorageExceptionCase.UNAUTHENTICATED_VIEW_REQUEST));

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .param("fileName", "photo.png"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void resolvesTheCallerFromAStreamTokenWhenNoHeaderIsPresent() throws Exception {
            byte[] data = "video bytes".getBytes();
            given(resolveViewIdentityUseCase.resolve(any())).willReturn(UUID.fromString(USER_ID));
            given(downloadFileUseCase.download(any())).willReturn(data);

            mockMvc.perform(get("/api/v1/storage/view/" + UUID.randomUUID())
                            .param("fileName", "clip.mp4")
                            .param("streamToken", "tok-1"))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                            .isEqualTo(data));
        }

        @Test
        void returnsPartialContentForARangeRequest() throws Exception {
            byte[] data = "0123456789".getBytes();
            given(resolveViewIdentityUseCase.resolve(any())).willReturn(UUID.fromString(USER_ID));
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
            given(resolveViewIdentityUseCase.resolve(any())).willReturn(UUID.fromString(USER_ID));
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

    @Nested
    @DisplayName("POST /api/v1/storage/stream-token")
    class IssueStreamToken {

        @Test
        void returnsTheIssuedToken() throws Exception {
            given(issueStreamTokenUseCase.issue(any())).willReturn("tok-1");

            mockMvc.perform(post("/api/v1/storage/stream-token")
                            .header("X_USER_ID", USER_ID)
                            .param("fileId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.streamToken").value("tok-1"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/storage/public/{token}/view")
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
    }
}
