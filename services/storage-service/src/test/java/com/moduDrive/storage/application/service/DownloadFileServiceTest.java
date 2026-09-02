package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
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
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId), userId)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId), userId)).willReturn(2);
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt()))
                    .willReturn(List.of("hello ".getBytes(), "world".getBytes()));

            byte[] result = downloadFileService.download(new DownloadFileCommand(fileId, userId));

            assertThat(new String(result)).isEqualTo("hello world");
        }

        @Test
        void assembleSingleBlockCorrectly() {
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId), userId)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId), userId)).willReturn(1);
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
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId), userId)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId), userId)).willReturn(2);
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            downloadFileService.downloadStream(new DownloadFileCommand(fileId, userId), out);

            then(retrieveBlocksPort).should().streamBlocks("files/abc/xyz", 2, out);
            then(retrieveBlocksPort).should(never()).retrieveBlocks(anyString(), anyInt());
        }

        @Test
        void chargesTheUserScopedQuotaTheNominalBlockPaddedSize() {
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId), userId)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId), userId)).willReturn(2);
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);

            downloadFileService.downloadStream(new DownloadFileCommand(fileId, userId), new ByteArrayOutputStream());

            then(downloadQuotaPort).should().recordAndEnforce(userId.toString(), "files/abc/xyz", 8_388_608L);
        }

        @Test
        void rejectsBeforeStreamingWhenTheFileIsOverItsDownloadQuota() {
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId), userId)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId), userId)).willReturn(2);
            willThrow(new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED))
                    .given(downloadQuotaPort).recordAndEnforce(anyString(), anyString(), anyLong());
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            Throwable thrown = catchThrowable(() ->
                    downloadFileService.downloadStream(new DownloadFileCommand(fileId, userId), out));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED);
            then(retrieveBlocksPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("인라인 미리보기(view)로 파일을 요청할 때")
    class WhenRequestedAsInlinePreview {

        @Test
        void alsoChargesTheDownloadQuotaSoItCannotBeUsedToBypassTheLimit() {
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId), userId)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId), userId)).willReturn(1);
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt())).willReturn(List.of("data".getBytes()));

            downloadFileService.download(new DownloadFileCommand(fileId, userId, true));

            then(downloadQuotaPort).should().recordAndEnforce(userId.toString(), "files/abc/xyz", 4_194_304L);
        }

        @Test
        void rejectsWithoutFetchingBlocksWhenOverQuota() {
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId), userId)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId), userId)).willReturn(1);
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);
            willThrow(new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED))
                    .given(downloadQuotaPort).recordAndEnforce(anyString(), anyString(), anyLong());

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
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId), userId)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId), userId)).willReturn(30);
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
