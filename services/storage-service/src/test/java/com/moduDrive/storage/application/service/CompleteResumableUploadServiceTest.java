package com.moduDrive.storage.application.service;

import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.storage.application.port.in.command.CompleteResumableUploadCommand;
import com.moduDrive.storage.application.port.out.FileUploadCallbackPort;
import com.moduDrive.storage.application.port.out.FindUploadSessionPort;
import com.moduDrive.storage.application.port.out.StoreBlocksPort;
import com.moduDrive.storage.domain.model.UploadSession;
import com.moduDrive.storage.exception.StorageExceptionCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CompleteResumableUploadServiceTest {

    @Mock private FindUploadSessionPort findUploadSessionPort;
    @Mock private StoreBlocksPort storeBlocksPort;
    @Mock private FileUploadCallbackPort callbackPort;
    @InjectMocks private CompleteResumableUploadService completeResumableUploadService;

    private final UUID userId = UUID.randomUUID();

    private UploadSession fullSession(int totalChunks) {
        UploadSession session = UploadSession.create(UUID.randomUUID(), userId, totalChunks);
        for (int i = 0; i < totalChunks; i++) {
            session.addChunk(i, ("chunk" + i).getBytes());
        }
        return session;
    }

    @Nested
    @DisplayName("완료 성공 시")
    class WhenCompleteSucceeds {

        @Test
        void storesChunksAndCallsBack() {
            UploadSession session = fullSession(2);
            given(findUploadSessionPort.findSession(session.getSessionId()))
                    .willReturn(Optional.of(session));
            given(storeBlocksPort.storeBlocks(anyString(), anyList())).willReturn(2);

            completeResumableUploadService.completeResumableUpload(
                    new CompleteResumableUploadCommand(session.getSessionId().toString(), userId));

            then(storeBlocksPort).should().storeBlocks(anyString(), anyList());
            then(callbackPort).should().notifyUploadComplete(any(UUID.class), anyLong(), anyInt(), anyString());
        }

        @Test
        void marksSessionCompleted() {
            UploadSession session = fullSession(1);
            given(findUploadSessionPort.findSession(session.getSessionId()))
                    .willReturn(Optional.of(session));
            given(storeBlocksPort.storeBlocks(anyString(), anyList())).willReturn(1);

            completeResumableUploadService.completeResumableUpload(
                    new CompleteResumableUploadCommand(session.getSessionId().toString(), userId));

            assertThat(session.isCompleted()).isTrue();
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

            assertThatThrownBy(() -> completeResumableUploadService.completeResumableUpload(
                    new CompleteResumableUploadCommand(unknownId, userId)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.SESSION_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("청크가 아직 모두 업로드되지 않았을 때")
    class WhenChunksIncomplete {

        @Test
        void throwsChunksIncomplete() {
            UploadSession session = UploadSession.create(UUID.randomUUID(), userId, 3);
            session.addChunk(0, "chunk0".getBytes());
            given(findUploadSessionPort.findSession(session.getSessionId()))
                    .willReturn(Optional.of(session));

            assertThatThrownBy(() -> completeResumableUploadService.completeResumableUpload(
                    new CompleteResumableUploadCommand(session.getSessionId().toString(), userId)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.CHUNKS_INCOMPLETE);
        }
    }

    @Nested
    @DisplayName("이미 완료된 세션일 때")
    class WhenAlreadyCompleted {

        @Test
        void throwsSessionAlreadyCompleted() {
            UploadSession session = fullSession(1);
            session.markCompleted();
            given(findUploadSessionPort.findSession(session.getSessionId()))
                    .willReturn(Optional.of(session));

            assertThatThrownBy(() -> completeResumableUploadService.completeResumableUpload(
                    new CompleteResumableUploadCommand(session.getSessionId().toString(), userId)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(StorageExceptionCase.SESSION_ALREADY_COMPLETED);
        }
    }
}
