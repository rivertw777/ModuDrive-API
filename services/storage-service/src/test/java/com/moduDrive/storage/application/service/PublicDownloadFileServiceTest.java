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

    private final String token = UUID.randomUUID().toString();

    @Nested
    @DisplayName("토큰이 공개 파일을 가리킬 때")
    class WhenTokenResolves {

        @Test
        void returnsAssembledBytes() {
            given(getFileVersionPort.getPublicS3Path(token)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getPublicBlockCount(token)).willReturn(2);
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt()))
                    .willReturn(List.of("hello ".getBytes(), "world".getBytes()));

            byte[] result = publicDownloadFileService.downloadPublic(new PublicDownloadFileCommand(token));

            assertThat(new String(result)).isEqualTo("hello world");
        }
    }

    @Nested
    @DisplayName("토큰이 더 이상 유효하지 않을 때")
    class WhenTokenIsRejected {

        @Test
        void propagatesNotFoundWithoutTouchingStorage() {
            willThrow(new BusinessException(StorageExceptionCase.FILE_NOT_FOUND_IN_STORAGE))
                    .given(getFileVersionPort).getPublicS3Path(token);

            Throwable thrown = catchThrowable(
                    () -> publicDownloadFileService.downloadPublic(new PublicDownloadFileCommand(token)));

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
            given(getFileVersionPort.getPublicS3Path(token)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getPublicBlockCount(token)).willReturn(2);

            publicDownloadFileService.downloadPublicStream(new PublicDownloadFileCommand(token), new ByteArrayOutputStream());

            then(retrieveBlocksPort).should().streamBlocks(eq("files/abc/xyz"), eq(2), any(OutputStream.class));
            then(retrieveBlocksPort).should(never()).retrieveBlocks(anyString(), anyInt());
        }

        @Test
        void checksTheTokenScopedQuotaBeforeStreamingAndRecordsWhatWasActuallySent() {
            given(getFileVersionPort.getPublicS3Path(token)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getPublicBlockCount(token)).willReturn(2);
            willAnswer(inv -> { ((OutputStream) inv.getArgument(2)).write(new byte[300]); return null; })
                    .given(retrieveBlocksPort).streamBlocks(anyString(), anyInt(), any());

            publicDownloadFileService.downloadPublicStream(new PublicDownloadFileCommand(token), new ByteArrayOutputStream());

            then(downloadQuotaPort).should().checkWithinQuota(token, "files/abc/xyz");
            then(downloadQuotaPort).should().recordUsage(token, "files/abc/xyz", 300L);
        }

        @Test
        void stillRecordsWhatWasSentWhenStreamingAbortsPartway() {
            given(getFileVersionPort.getPublicS3Path(token)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getPublicBlockCount(token)).willReturn(2);
            willAnswer(inv -> {
                ((OutputStream) inv.getArgument(2)).write(new byte[128]);
                throw new UncheckedIOException(new IOException("client gone"));
            }).given(retrieveBlocksPort).streamBlocks(anyString(), anyInt(), any());

            catchThrowable(() -> publicDownloadFileService.downloadPublicStream(
                    new PublicDownloadFileCommand(token), new ByteArrayOutputStream()));

            then(downloadQuotaPort).should().recordUsage(token, "files/abc/xyz", 128L);
        }

        @Test
        void rejectsBeforeStreamingWhenTheFileIsOverItsDownloadQuota() {
            given(getFileVersionPort.getPublicS3Path(token)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getPublicBlockCount(token)).willReturn(2);
            willThrow(new BusinessException(StorageExceptionCase.DOWNLOAD_QUOTA_EXCEEDED))
                    .given(downloadQuotaPort).checkWithinQuota(anyString(), anyString());

            Throwable thrown = catchThrowable(() -> publicDownloadFileService.downloadPublicStream(
                    new PublicDownloadFileCommand(token), new ByteArrayOutputStream()));

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
            given(getFileVersionPort.getPublicS3Path(token)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getPublicBlockCount(token)).willReturn(1);
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt())).willReturn(List.of("data".getBytes()));

            publicDownloadFileService.downloadPublic(new PublicDownloadFileCommand(token, true));

            then(downloadQuotaPort).should().checkWithinQuota(token, "files/abc/xyz");
            then(downloadQuotaPort).should().recordUsage(token, "files/abc/xyz", 4L);
        }
    }

    @Nested
    @DisplayName("토큰이 폴더를 가리키고 entryId가 함께 올 때")
    class WhenTokenIsAFolderLinkWithAnEntry {

        private final String entryId = UUID.randomUUID().toString();

        @Test
        void resolvesTheDescendantVersionInOneLookup() {
            given(getFileVersionPort.getPublicDescendantVersion(token, entryId))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 2));
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt()))
                    .willReturn(List.of("hello ".getBytes(), "world".getBytes()));

            byte[] result = publicDownloadFileService.downloadPublic(
                    new PublicDownloadFileCommand(token, entryId, false));

            assertThat(new String(result)).isEqualTo("hello world");
            then(getFileVersionPort).should(never()).getPublicS3Path(anyString());
        }

        @Test
        void streamsTheDescendantWithoutAssembling() {
            given(getFileVersionPort.getPublicDescendantVersion(token, entryId))
                    .willReturn(new GetFileVersionPort.VersionLocation("files/abc/xyz", 2));

            publicDownloadFileService.downloadPublicStream(
                    new PublicDownloadFileCommand(token, entryId, false), new ByteArrayOutputStream());

            then(retrieveBlocksPort).should().streamBlocks(eq("files/abc/xyz"), eq(2), any(OutputStream.class));
        }
    }

    @Nested
    @DisplayName("인라인 미리보기 요청의 파일이 용량 제한을 넘을 때")
    class WhenInlinePreviewExceedsTheSizeCap {

        @Test
        void rejectsBeforeFetchingAnyBlocks() {
            given(getFileVersionPort.getPublicS3Path(token)).willReturn("files/abc/xyz");
            given(getFileVersionPort.getPublicBlockCount(token)).willReturn(30);
            given(storageProperties.getBlockSize()).willReturn(4 * 1024 * 1024);

            Throwable thrown = catchThrowable(() ->
                    publicDownloadFileService.downloadPublic(new PublicDownloadFileCommand(token, true)));

            assertThat(thrown).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.PREVIEW_TOO_LARGE);
            then(retrieveBlocksPort).shouldHaveNoInteractions();
        }
    }
}
