package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.PublicDownloadFileCommand;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import com.moduDrive.storage.config.StorageProperties;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class PublicDownloadFileServiceTest {

    @Mock private GetFileVersionPort getFileVersionPort;
    @Mock private RetrieveBlocksPort retrieveBlocksPort;
    @Mock private DownloadQuotaPort downloadQuotaPort;
    @Mock private StorageProperties storageProperties;
    @InjectMocks private PublicDownloadFileService publicDownloadFileService;

    private final String fileId = UUID.randomUUID().toString();
    private final String key = UUID.randomUUID().toString();

    private PublicDownloadFileCommand command() {
        return new PublicDownloadFileCommand(fileId, key);
    }

    private PublicDownloadFileCommand previewCommand() {
        return new PublicDownloadFileCommand(fileId, key, true);
    }

    @Nested
    @DisplayName("fileId/key가 공개 파일을 가리킬 때")
    class WhenKeyResolves {

        @Test
        void returnsAssembledBytes() {
            given(getFileVersionPort.getPublicVersion(fileId, key))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 2));
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt()))
                    .willReturn(List.of("hello ".getBytes(), "world".getBytes()));

            byte[] result = publicDownloadFileService.downloadPublic(command());

            assertThat(new String(result)).isEqualTo("hello world");
        }
    }

    @Nested
    @DisplayName("key가 더 이상 유효하지 않을 때")
    class WhenKeyIsRejected {

        @Test
        void propagatesNotFoundWithoutTouchingStorage() {
            willThrow(new BusinessException(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE))
                    .given(getFileVersionPort).getPublicVersion(fileId, key);

            Throwable thrown = catchThrowable(
                    () -> publicDownloadFileService.downloadPublic(command()));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE);
            then(retrieveBlocksPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("공개 다운로드를 스트리밍으로 요청할 때")
    class WhenDownloadingAsStream {

        @Test
        void delegatesToStreamBlocksWithoutAssemblingAByteArray() {
            given(getFileVersionPort.getPublicVersion(fileId, key))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 2));

            publicDownloadFileService.downloadPublicStream(command(), new ByteArrayOutputStream());

            then(retrieveBlocksPort).should().streamBlocks(eq("files/abc/xyz"), eq(2), any(OutputStream.class));
            then(retrieveBlocksPort).should(never()).retrieveBlocks(anyString(), anyInt());
        }

        @Test
        void checksTheKeyScopedQuotaBeforeStreamingAndRecordsWhatWasActuallySent() {
            given(getFileVersionPort.getPublicVersion(fileId, key))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 2));
            willAnswer(inv -> { ((OutputStream) inv.getArgument(2)).write(new byte[300]); return null; })
                    .given(retrieveBlocksPort).streamBlocks(anyString(), anyInt(), any());

            publicDownloadFileService.downloadPublicStream(command(), new ByteArrayOutputStream());

            then(downloadQuotaPort).should().checkWithinQuota(key, "files/abc/xyz");
            then(downloadQuotaPort).should().recordUsage(key, "files/abc/xyz", 300L);
        }

        @Test
        void stillRecordsWhatWasSentWhenStreamingAbortsPartway() {
            given(getFileVersionPort.getPublicVersion(fileId, key))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 2));
            willAnswer(inv -> {
                ((OutputStream) inv.getArgument(2)).write(new byte[128]);
                throw new UncheckedIOException(new IOException("client gone"));
            }).given(retrieveBlocksPort).streamBlocks(anyString(), anyInt(), any());

            catchThrowable(() -> publicDownloadFileService.downloadPublicStream(
                    command(), new ByteArrayOutputStream()));

            then(downloadQuotaPort).should().recordUsage(key, "files/abc/xyz", 128L);
        }

        @Test
        void rejectsBeforeStreamingWhenTheFileIsOverItsDownloadQuota() {
            given(getFileVersionPort.getPublicVersion(fileId, key))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 2));
            willThrow(new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED))
                    .given(downloadQuotaPort).checkWithinQuota(anyString(), anyString());

            Throwable thrown = catchThrowable(() -> publicDownloadFileService.downloadPublicStream(
                    command(), new ByteArrayOutputStream()));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED);
            then(retrieveBlocksPort).shouldHaveNoInteractions();
            then(downloadQuotaPort).should(never()).recordUsage(anyString(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("공개 인라인 미리보기(view)로 요청할 때")
    class WhenRequestedAsPublicInlinePreview {

        @Test
        void alsoMetersTheQuotaSoTheViewRouteCannotBypassTheLimit() {
            given(getFileVersionPort.getPublicVersion(fileId, key))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 1));
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt())).willReturn(List.of("data".getBytes()));

            publicDownloadFileService.downloadPublic(previewCommand());

            then(downloadQuotaPort).should().checkWithinQuota(key, "files/abc/xyz");
            then(downloadQuotaPort).should().recordUsage(key, "files/abc/xyz", 4L);
        }
    }

    @Nested
    @DisplayName("fileId가 공유 폴더 하위의 파일일 때")
    class WhenFileIsUnderASharedFolder {

        @Test
        void resolvesItThroughTheSameKeyedLookup() {
            given(getFileVersionPort.getPublicVersion(fileId, key))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 2));
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt()))
                    .willReturn(List.of("hello ".getBytes(), "world".getBytes()));

            byte[] result = publicDownloadFileService.downloadPublic(command());

            assertThat(new String(result)).isEqualTo("hello world");
        }
    }

    @Nested
    @DisplayName("인라인 미리보기 요청의 파일이 용량 제한을 넘을 때")
    class WhenInlinePreviewExceedsTheSizeCap {

        @Test
        void rejectsBeforeFetchingAnyBlocks() {
            given(getFileVersionPort.getPublicVersion(fileId, key))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 30));
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);

            Throwable thrown = catchThrowable(() ->
                    publicDownloadFileService.downloadPublic(previewCommand()));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.PREVIEW_TOO_LARGE);
            then(retrieveBlocksPort).shouldHaveNoInteractions();
        }
    }
}
