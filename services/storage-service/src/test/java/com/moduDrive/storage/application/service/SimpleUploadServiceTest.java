package com.moduDrive.storage.application.service;

import com.moduDrive.storage.application.port.in.command.SimpleUploadCommand;
import com.moduDrive.storage.application.port.out.FileUploadCallbackPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class SimpleUploadServiceTest {

    @Mock private StoreBlocksPort storeBlocksPort;
    @Mock private FileUploadCallbackPort callbackPort;
    @InjectMocks private SimpleUploadService simpleUploadService;

    private final String fileId = UUID.randomUUID().toString();
    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("파일 업로드 성공 시")
    class WhenUploadSucceeds {

        @Test
        void storesBlocksAndCallsBack() {
            byte[] data = "hello world".getBytes();
            SimpleUploadCommand command = new SimpleUploadCommand(fileId, userId, data);
            given(storeBlocksPort.storeBlocks(anyString(), anyList())).willReturn(1);

            simpleUploadService.simpleUpload(command);

            then(storeBlocksPort).should().storeBlocks(anyString(), anyList());
            then(callbackPort).should().notifyUploadComplete(
                    any(UUID.class), any(UUID.class), any(Long.class), any(Integer.class), anyString());
        }

        @Test
        void callbackReceivesCorrectFileSize() {
            byte[] data = new byte[100];
            SimpleUploadCommand command = new SimpleUploadCommand(fileId, userId, data);
            given(storeBlocksPort.storeBlocks(anyString(), anyList())).willReturn(1);

            ArgumentCaptor<Long> sizeCaptor = ArgumentCaptor.forClass(Long.class);
            simpleUploadService.simpleUpload(command);

            then(callbackPort).should().notifyUploadComplete(
                    any(UUID.class), any(UUID.class), sizeCaptor.capture(), any(Integer.class), anyString());
            assertThat(sizeCaptor.getValue()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("빈 파일 업로드 시")
    class WhenEmptyFile {

        @Test
        void storesSingleEmptyBlock() {
            SimpleUploadCommand command = new SimpleUploadCommand(fileId, userId, new byte[0]);
            given(storeBlocksPort.storeBlocks(anyString(), anyList())).willReturn(1);

            simpleUploadService.simpleUpload(command);

            then(storeBlocksPort).should(times(1)).storeBlocks(anyString(), anyList());
        }
    }
}
