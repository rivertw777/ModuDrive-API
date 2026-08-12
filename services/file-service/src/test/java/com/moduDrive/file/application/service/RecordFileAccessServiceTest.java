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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;

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
}
