package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.InitResumableUploadCommand;
import com.moduDrive.storage.application.port.out.CreateUploadSessionPort;
import com.moduDrive.storage.domain.model.UploadSession;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class InitResumableUploadServiceTest {

    private static final long TEST_MAX_FILE_SIZE_BYTES = 5_368_709_120L; // 5GB

    @Mock private CreateUploadSessionPort createUploadSessionPort;
    private InitResumableUploadService initResumableUploadService;

    private final String fileId = UUID.randomUUID().toString();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        initResumableUploadService = new InitResumableUploadService(createUploadSessionPort, TEST_MAX_FILE_SIZE_BYTES);
    }

    @Nested
    @DisplayName("재개 가능 업로드 세션 초기화 시")
    class WhenInitResumableUpload {

        @Test
        void returnsSessionId() {
            InitResumableUploadCommand command = new InitResumableUploadCommand(fileId, userId, 5, 1_000_000L);

            UUID sessionId = initResumableUploadService.initResumableUpload(command);

            assertThat(sessionId).isNotNull();
        }

        @Test
        void savesSessionWithCorrectFields() {
            InitResumableUploadCommand command = new InitResumableUploadCommand(fileId, userId, 3, 1_000_000L);
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
            InitResumableUploadCommand command = new InitResumableUploadCommand(fileId, userId, 2, 1_000_000L);
            ArgumentCaptor<UploadSession> captor = ArgumentCaptor.forClass(UploadSession.class);

            UUID returned = initResumableUploadService.initResumableUpload(command);

            then(createUploadSessionPort).should().createSession(captor.capture());
            assertThat(captor.getValue().getSessionId()).isEqualTo(returned);
        }
    }

    @Nested
    @DisplayName("파일 크기가 5GB를 초과할 때")
    class WhenFileTooLarge {

        @Test
        void throwsBusinessException() {
            InitResumableUploadCommand command =
                    new InitResumableUploadCommand(fileId, userId, 1024, TEST_MAX_FILE_SIZE_BYTES + 1);

            Throwable thrown = catchThrowable(() -> initResumableUploadService.initResumableUpload(command));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.FILE_TOO_LARGE);
            then(createUploadSessionPort).shouldHaveNoInteractions();
        }
    }
}
