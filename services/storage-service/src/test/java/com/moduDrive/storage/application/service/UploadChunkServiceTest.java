package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.UploadChunkCommand;
import com.moduDrive.storage.application.port.out.FindUploadSessionPort;
import com.moduDrive.storage.domain.model.UploadSession;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UploadChunkServiceTest {

    private static final long TEST_MAX_FILE_SIZE_BYTES = 5_368_709_120L; // 5GB

    @Mock private FindUploadSessionPort findUploadSessionPort;
    private UploadChunkService uploadChunkService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        uploadChunkService = new UploadChunkService(findUploadSessionPort, TEST_MAX_FILE_SIZE_BYTES);
    }

    private UploadSession activeSession() {
        return UploadSession.create(UUID.randomUUID(), userId, 3);
    }

    @Nested
    @DisplayName("청크 업로드 성공 시")
    class WhenUploadSucceeds {

        @Test
        void addsChunkToSession() {
            UploadSession session = activeSession();
            given(findUploadSessionPort.findSession(session.getSessionId()))
                    .willReturn(Optional.of(session));
            // Same value as userId but a different object — regression guard for the
            // reference-equality bug this service used to have.
            UploadChunkCommand command = new UploadChunkCommand(
                    session.getSessionId().toString(), UUID.fromString(userId.toString()), 0, "chunk0".getBytes());

            uploadChunkService.uploadChunk(command);

            assertThat(session.getChunks()).containsKey(0);
        }
    }

    @Nested
    @DisplayName("세션을 찾을 수 없을 때")
    class WhenSessionNotFound {

        @Test
        void throwsSessionNotFound() {
            String unknownId = UUID.randomUUID().toString();
            given(findUploadSessionPort.findSession(UUID.fromString(unknownId)))
                    .willReturn(Optional.empty());
            UploadChunkCommand command = new UploadChunkCommand(unknownId, userId, 0, new byte[1]);

            assertThatThrownBy(() -> uploadChunkService.uploadChunk(command))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.SESSION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("다른 사용자의 세션에 접근할 때")
    class WhenOwnerMismatch {

        @Test
        void throwsSessionOwnerMismatch() {
            UploadSession session = activeSession();
            given(findUploadSessionPort.findSession(session.getSessionId()))
                    .willReturn(Optional.of(session));
            UploadChunkCommand command = new UploadChunkCommand(
                    session.getSessionId().toString(), UUID.randomUUID(), 0, new byte[1]);

            assertThatThrownBy(() -> uploadChunkService.uploadChunk(command))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.SESSION_OWNER_MISMATCH);
        }
    }

    @Nested
    @DisplayName("chunkIndex가 세션의 totalChunks를 벗어날 때")
    class WhenChunkIndexOutOfRange {

        @Test
        void throwsInvalidChunkIndex() {
            UploadSession session = activeSession(); // totalChunks = 3, valid indices 0..2
            given(findUploadSessionPort.findSession(session.getSessionId()))
                    .willReturn(Optional.of(session));
            UploadChunkCommand command = new UploadChunkCommand(
                    session.getSessionId().toString(), userId, 3, new byte[1]);

            assertThatThrownBy(() -> uploadChunkService.uploadChunk(command))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.INVALID_CHUNK_INDEX);
        }
    }

    @Nested
    @DisplayName("누적 청크 크기가 최대 파일 크기를 초과할 때")
    class WhenAccumulatedSizeExceedsLimit {

        @Test
        void throwsFileTooLargeWithoutBufferingTheChunk() {
            // A small limit here (instead of the class-wide 5GB constant) keeps this test
            // from actually allocating gigabytes in the test JVM.
            UploadChunkService smallLimitService = new UploadChunkService(findUploadSessionPort, 15L);
            UploadSession session = activeSession();
            session.addChunk(1, new byte[10]);
            given(findUploadSessionPort.findSession(session.getSessionId()))
                    .willReturn(Optional.of(session));
            UploadChunkCommand command = new UploadChunkCommand(
                    session.getSessionId().toString(), userId, 0, new byte[10]);

            assertThatThrownBy(() -> smallLimitService.uploadChunk(command))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.FILE_TOO_LARGE);
            // The chunk that pushed the total over the limit must never land in the session.
            assertThat(session.getChunks()).doesNotContainKey(0);
        }
    }

    @Nested
    @DisplayName("이미 완료된 세션에 청크를 업로드할 때")
    class WhenSessionCompleted {

        @Test
        void throwsSessionAlreadyCompleted() {
            UploadSession session = activeSession();
            session.markCompleted();
            given(findUploadSessionPort.findSession(session.getSessionId()))
                    .willReturn(Optional.of(session));
            UploadChunkCommand command = new UploadChunkCommand(
                    session.getSessionId().toString(), userId, 0, new byte[1]);

            assertThatThrownBy(() -> uploadChunkService.uploadChunk(command))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.SESSION_ALREADY_COMPLETED);
        }
    }
}
