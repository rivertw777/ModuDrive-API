package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
import com.moduDrive.storage.application.port.out.DownloadQuotaPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.GetFileVersionPort.VersionLocation;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
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
class DownloadFileServiceTest {

    @Mock private GetFileVersionPort getFileVersionPort;
    @Mock private RetrieveBlocksPort retrieveBlocksPort;
    @Mock private DownloadQuotaPort downloadQuotaPort;
    @Mock private StorageProperties storageProperties;
    @InjectMocks private DownloadFileService downloadFileService;

    private final String fileId = UUID.randomUUID().toString();
    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("파일 다운로드 성공 시")
    class WhenDownloadSucceeds {

        @Test
        void returnsAssembledBytes() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 2));
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt()))
                    .willReturn(List.of("hello ".getBytes(), "world".getBytes()));

            byte[] result = downloadFileService.download(new DownloadFileCommand(fileId, userId));

            assertThat(new String(result)).isEqualTo("hello world");
        }

        @Test
        void assembleSingleBlockCorrectly() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 1));
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt()))
                    .willReturn(List.of("data".getBytes()));

            byte[] result = downloadFileService.download(new DownloadFileCommand(fileId, userId));

            assertThat(result).isEqualTo("data".getBytes());
        }
    }

    @Nested
    @DisplayName("일반 다운로드를 스트리밍으로 요청할 때")
    class WhenDownloadingAsStream {

        @Test
        void delegatesToStreamBlocksWithoutAssemblingAByteArray() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 2));

            downloadFileService.downloadStream(new DownloadFileCommand(fileId, userId), new ByteArrayOutputStream());

            then(retrieveBlocksPort).should().streamBlocks(eq("files/abc/xyz"), eq(2), any(OutputStream.class));
            then(retrieveBlocksPort).should(never()).retrieveBlocks(anyString(), anyInt());
        }

        @Test
        void doesNotMarkTheFileAsRecentlyAccessed() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 2));

            downloadFileService.downloadStream(new DownloadFileCommand(fileId, userId), new ByteArrayOutputStream());

            then(getFileVersionPort).should().getLatestVersion(UUID.fromString(fileId), userId, false);
        }

        @Test
        void checksTheUserScopedQuotaBeforeStreaming() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 2));

            downloadFileService.downloadStream(new DownloadFileCommand(fileId, userId), new ByteArrayOutputStream());

            then(downloadQuotaPort).should().checkWithinQuota(userId.toString(), "files/abc/xyz");
        }

        @Test
        void recordsOnlyTheBytesThatActuallyReachedTheClient() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 2));
            willAnswer(inv -> { ((OutputStream) inv.getArgument(2)).write(new byte[512]); return null; })
                    .given(retrieveBlocksPort).streamBlocks(anyString(), anyInt(), any());

            downloadFileService.downloadStream(new DownloadFileCommand(fileId, userId), new ByteArrayOutputStream());

            then(downloadQuotaPort).should().recordUsage(userId.toString(), "files/abc/xyz", 512L);
        }

        @Test
        void stillRecordsWhatWasSentWhenStreamingAbortsPartway() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 2));
            willAnswer(inv -> {
                ((OutputStream) inv.getArgument(2)).write(new byte[100]);
                throw new UncheckedIOException(new IOException("client gone"));
            }).given(retrieveBlocksPort).streamBlocks(anyString(), anyInt(), any());

            catchThrowable(() -> downloadFileService.downloadStream(
                    new DownloadFileCommand(fileId, userId), new ByteArrayOutputStream()));

            then(downloadQuotaPort).should().recordUsage(userId.toString(), "files/abc/xyz", 100L);
        }

        @Test
        void rejectsBeforeStreamingWhenTheFileIsOverItsDownloadQuota() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 2));
            willThrow(new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED))
                    .given(downloadQuotaPort).checkWithinQuota(anyString(), anyString());

            Throwable thrown = catchThrowable(() -> downloadFileService.downloadStream(
                    new DownloadFileCommand(fileId, userId), new ByteArrayOutputStream()));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED);
            then(retrieveBlocksPort).shouldHaveNoInteractions();
            then(downloadQuotaPort).should(never()).recordUsage(anyString(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("인라인 미리보기(view)로 파일을 요청할 때")
    class WhenRequestedAsInlinePreview {

        @Test
        void alsoMetersTheQuotaByRealSizeSoItCannotBeUsedToBypassTheLimit() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 1));
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt())).willReturn(List.of("data".getBytes()));

            downloadFileService.download(new DownloadFileCommand(fileId, userId, true));

            then(downloadQuotaPort).should().checkWithinQuota(userId.toString(), "files/abc/xyz");
            then(downloadQuotaPort).should().recordUsage(userId.toString(), "files/abc/xyz", 4L);
        }

        @Test
        void marksTheFileAsRecentlyAccessed() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 1));
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt())).willReturn(List.of("data".getBytes()));

            downloadFileService.download(new DownloadFileCommand(fileId, userId, true));

            then(getFileVersionPort).should().getLatestVersion(UUID.fromString(fileId), userId, true);
        }

        @Test
        void rejectsWithoutFetchingBlocksWhenOverQuota() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 1));
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);
            willThrow(new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED))
                    .given(downloadQuotaPort).checkWithinQuota(anyString(), anyString());

            Throwable thrown = catchThrowable(() ->
                    downloadFileService.download(new DownloadFileCommand(fileId, userId, true)));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED);
            then(retrieveBlocksPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("인라인 미리보기 요청의 파일이 용량 제한을 넘을 때")
    class WhenInlinePreviewExceedsTheSizeCap {

        @Test
        void rejectsBeforeFetchingAnyBlocks() {
            given(getFileVersionPort.getLatestVersion(any(), any(), anyBoolean())).willReturn(new VersionLocation("files/abc/xyz", 30));
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);

            Throwable thrown = catchThrowable(() ->
                    downloadFileService.download(new DownloadFileCommand(fileId, userId, true)));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.PREVIEW_TOO_LARGE);
            then(retrieveBlocksPort).shouldHaveNoInteractions();
        }
    }
}
