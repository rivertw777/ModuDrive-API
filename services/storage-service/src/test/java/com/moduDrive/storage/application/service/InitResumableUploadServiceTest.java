package com.moduDrive.storage.application.service;

import com.moduDrive.storage.application.port.in.command.InitResumableUploadCommand;
import com.moduDrive.storage.application.port.out.CreateUploadSessionPort;
import com.moduDrive.storage.domain.model.UploadSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class InitResumableUploadServiceTest {

    @Mock private CreateUploadSessionPort createUploadSessionPort;
    @InjectMocks private InitResumableUploadService initResumableUploadService;

    private final String fileId = UUID.randomUUID().toString();
    private final long userId = 1L;

    @Nested
    @DisplayName("재개 가능 업로드 세션 초기화 시")
    class WhenInitResumableUpload {

        @Test
        void returnsSessionId() {
            InitResumableUploadCommand command = new InitResumableUploadCommand(fileId, userId, 5);

            UUID sessionId = initResumableUploadService.initResumableUpload(command);

            assertThat(sessionId).isNotNull();
        }

        @Test
        void savesSessionWithCorrectFields() {
            InitResumableUploadCommand command = new InitResumableUploadCommand(fileId, userId, 3);
            ArgumentCaptor<UploadSession> captor = ArgumentCaptor.forClass(UploadSession.class);

            initResumableUploadService.initResumableUpload(command);

            then(createUploadSessionPort).should().createSession(captor.capture());
            UploadSession saved = captor.getValue();
            assertThat(saved.getFileId()).isEqualTo(UUID.fromString(fileId));
            assertThat(saved.getOwnerId()).isEqualTo(userId);
            assertThat(saved.getTotalChunks()).isEqualTo(3);
            assertThat(saved.isCompleted()).isFalse();
        }

        @Test
        void sessionIdMatchesReturnedValue() {
            InitResumableUploadCommand command = new InitResumableUploadCommand(fileId, userId, 2);
            ArgumentCaptor<UploadSession> captor = ArgumentCaptor.forClass(UploadSession.class);

            UUID returned = initResumableUploadService.initResumableUpload(command);

            then(createUploadSessionPort).should().createSession(captor.capture());
            assertThat(captor.getValue().getSessionId()).isEqualTo(returned);
        }
    }
}
