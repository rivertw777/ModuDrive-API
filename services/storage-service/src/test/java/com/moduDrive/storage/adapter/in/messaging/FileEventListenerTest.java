package com.moduDrive.storage.adapter.in.messaging;

import com.moduDrive.common.event.file.BlocksPurgeRequested;
import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand;
import com.moduDrive.storage.application.port.in.command.PurgeStoredFileCommand.StoredVersion;
import com.moduDrive.storage.application.port.in.usecase.PurgeStoredFileUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FileEventListenerTest {

    @Mock private PurgeStoredFileUseCase purgeStoredFileUseCase;
    @InjectMocks private FileEventListener listener;

    @Test
    @DisplayName("블록 삭제 요청을 받으면 이벤트에 담긴 모든 버전의 블록 삭제를 맡긴다")
    void purgesEveryVersionInTheEvent() {
        UUID fileId = UUID.randomUUID();

        listener.onBlocksPurgeRequested(new BlocksPurgeRequested(fileId, List.of(
                new BlocksPurgeRequested.StoredVersion("path/v1", 2),
                new BlocksPurgeRequested.StoredVersion("path/v2", 4))));

        ArgumentCaptor<PurgeStoredFileCommand> command = ArgumentCaptor.forClass(PurgeStoredFileCommand.class);
        then(purgeStoredFileUseCase).should().purgeStoredFile(command.capture());
        assertThat(command.getValue().getFileId()).isEqualTo(fileId);
        assertThat(command.getValue().getVersions())
                .containsExactly(new StoredVersion("path/v1", 2), new StoredVersion("path/v2", 4));
    }
}
