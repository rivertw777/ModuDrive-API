package com.moduDrive.storage.adapter.in.messaging;

import com.moduDrive.common.event.file.BlocksPurgeRequested;
import com.moduDrive.storage.application.port.in.command.PurgeBlocksCommand;
import com.moduDrive.storage.application.port.in.usecase.PurgeBlocksUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FileEventListenerTest {

    @Mock private PurgeBlocksUseCase purgeBlocksUseCase;
    @InjectMocks private FileEventListener listener;

    @Test
    @DisplayName("블록 삭제 요청을 받으면 소유자·해시·결정 시각을 그대로 넘긴다")
    void passesTheEventThrough() {
        UUID ownerId = UUID.randomUUID();
        Instant decidedAt = Instant.parse("2026-10-08T00:00:00Z");

        listener.onBlocksPurgeRequested(new BlocksPurgeRequested(ownerId, List.of("h1", "h2"), decidedAt));

        ArgumentCaptor<PurgeBlocksCommand> command = ArgumentCaptor.forClass(PurgeBlocksCommand.class);
        then(purgeBlocksUseCase).should().purgeBlocks(command.capture());
        assertThat(command.getValue().getOwnerId()).isEqualTo(ownerId);
        assertThat(command.getValue().getHashes()).containsExactly("h1", "h2");
        assertThat(command.getValue().getDecidedAt()).isEqualTo(decidedAt);
    }
}
