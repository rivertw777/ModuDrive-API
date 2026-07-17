package com.moduDrive.storage.application.service;

import com.moduDrive.storage.application.port.in.command.DownloadFileCommand;
import com.moduDrive.storage.application.port.out.GetFileVersionPort;
import com.moduDrive.storage.application.port.out.RetrieveBlocksPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DownloadFileServiceTest {

    @Mock private GetFileVersionPort getFileVersionPort;
    @Mock private RetrieveBlocksPort retrieveBlocksPort;
    @InjectMocks private DownloadFileService downloadFileService;

    private final String fileId = UUID.randomUUID().toString();

    @Nested
    @DisplayName("파일 다운로드 성공 시")
    class WhenDownloadSucceeds {

        @Test
        void returnsAssembledBytes() {
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId))).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId))).willReturn(2);
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt()))
                    .willReturn(List.of("hello ".getBytes(), "world".getBytes()));

            byte[] result = downloadFileService.download(new DownloadFileCommand(fileId));

            assertThat(new String(result)).isEqualTo("hello world");
        }

        @Test
        void assembleSingleBlockCorrectly() {
            given(getFileVersionPort.getS3Path(UUID.fromString(fileId))).willReturn("files/abc/xyz");
            given(getFileVersionPort.getBlockCount(UUID.fromString(fileId))).willReturn(1);
            given(retrieveBlocksPort.retrieveBlocks(anyString(), anyInt()))
                    .willReturn(List.of("data".getBytes()));

            byte[] result = downloadFileService.download(new DownloadFileCommand(fileId));

            assertThat(result).isEqualTo("data".getBytes());
        }
    }
}
