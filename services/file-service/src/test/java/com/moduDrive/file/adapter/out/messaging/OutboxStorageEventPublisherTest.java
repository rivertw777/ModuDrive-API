package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.event.file.BlocksPurgeRequested;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OutboxStorageEventPublisherTest {

    @Mock
    private OutboxEventRecorder outboxEventRecorder;
    @InjectMocks
    private OutboxStorageEventPublisher publisher;

    private final UUID ownerId = UUID.randomUUID();
    private final Instant decidedAt = Instant.parse("2026-10-08T00:00:00Z");

    @Nested
    @DisplayName("지울 블록이 있을 때")
    class WhenThereAreHashes {

        @Test
        @DisplayName("해시와 결정 시각을 담아 ownerId를 키로 기록한다")
        void recordsHashesKeyedByOwnerId() {
            publisher.purgeBlocks(ownerId, List.of("h1", "h2"), decidedAt);

            then(outboxEventRecorder).should().record(FileQueues.BLOCKS_PURGE_REQUESTED, ownerId.toString(),
                    new BlocksPurgeRequested(ownerId, List.of("h1", "h2"), decidedAt));
        }
    }

    @Nested
    @DisplayName("지울 블록이 없으면")
    class WhenThereAreNoHashes {

        @Test
        @DisplayName("기록하지 않는다")
        void recordsNothing() {
            publisher.purgeBlocks(ownerId, List.of(), decidedAt);

            then(outboxEventRecorder).shouldHaveNoInteractions();
        }
    }
}
