package com.moduDrive.file.adapter.out.messaging;

import com.moduDrive.common.event.file.BlocksPurgeRequested;
import com.moduDrive.common.event.file.BlocksPurgeRequested.StoredVersion;
import com.moduDrive.common.event.file.FileQueues;
import com.moduDrive.common.infrastructure.messaging.outbox.OutboxEventRecorder;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;
import com.moduDrive.file.domain.model.FileVersion.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OutboxStorageEventPublisherTest {

    @Mock
    private OutboxEventRecorder outboxEventRecorder;
    @InjectMocks
    private OutboxStorageEventPublisher publisher;

    private final UUID fileId = UUID.randomUUID();

    private FileVersion version(String s3Path, int blockCount) {
        return FileVersion.withId(new FileVersionId(UUID.randomUUID()), new FileVersionFileId(fileId),
                new FileVersionFileSize(10L), new FileVersionBlockCount(blockCount), new FileVersionS3Path(s3Path));
    }

    @Nested
    @DisplayName("버전이 있는 파일의 블록 삭제를 요청할 때")
    class WhenTheFileHasVersions {

        @Test
        @DisplayName("모든 버전의 블록 위치를 담아 fileId를 키로 기록한다")
        void recordsEveryVersionLocationKeyedByFileId() {
            publisher.purgeBlocks(new FileId(fileId), List.of(version("blocks/v1", 2), version("blocks/v2", 4)));

            then(outboxEventRecorder).should().record(FileQueues.BLOCKS_PURGE_REQUESTED, fileId.toString(),
                    new BlocksPurgeRequested(fileId, List.of(
                            new StoredVersion("blocks/v1", 2), new StoredVersion("blocks/v2", 4))));
        }
    }

    @Nested
    @DisplayName("버전이 없는 파일이면")
    class WhenTheFileHasNoVersions {

        @Test
        @DisplayName("지울 블록이 없으니 기록하지 않는다")
        void recordsNothing() {
            publisher.purgeBlocks(new FileId(fileId), List.of());

            then(outboxEventRecorder).shouldHaveNoInteractions();
        }
    }
}
