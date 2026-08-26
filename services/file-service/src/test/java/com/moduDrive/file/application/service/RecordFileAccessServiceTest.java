package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.in.command.RecordFileAccessCommand;
import com.moduDrive.file.application.port.out.SaveFileAccessPort;
import com.moduDrive.file.domain.model.FileAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class RecordFileAccessServiceTest {

    @Mock private SaveFileAccessPort saveFileAccessPort;
    @InjectMocks private RecordFileAccessService recordFileAccessService;

    private final RecordFileAccessCommand command = new RecordFileAccessCommand(UUID.randomUUID(), UUID.randomUUID());

    @Nested
    @DisplayName("파일 접근을 기록할 때")
    class WhenRecordingAccess {

        @Test
        void savesFileAccess() {
            recordFileAccessService.recordAccess(command);

            then(saveFileAccessPort).should().recordAccess(any(FileAccess.class));
        }
    }

    @Nested
    @DisplayName("접근 기록 저장이 실패할 때")
    class WhenSavingAccessFails {

        @Test
        void swallowsTheFailure() {
            willThrow(new RuntimeException("db hiccup")).given(saveFileAccessPort).recordAccess(any(FileAccess.class));

            assertThatCode(() -> recordFileAccessService.recordAccess(command)).doesNotThrowAnyException();
        }
    }
}
