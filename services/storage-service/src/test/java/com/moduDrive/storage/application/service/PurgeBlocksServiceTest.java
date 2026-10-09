package com.moduDrive.storage.application.service;

import com.moduDrive.storage.application.port.in.command.PurgeBlocksCommand;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import com.moduDrive.storage.application.port.out.FindUploadedBlocksPort;
import com.moduDrive.storage.domain.model.Blocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PurgeBlocksServiceTest {

    @Mock private DeleteBlocksPort deleteBlocksPort;
    @Mock private FindUploadedBlocksPort findUploadedBlocksPort;
    @InjectMocks private PurgeBlocksService service;

    private final UUID ownerId = UUID.randomUUID();
    private final Instant decidedAt = Instant.parse("2026-10-08T00:00:00Z");

    @Test
    @DisplayName("소유자 공간의 블록마다 결정 시각을 붙여 지운다")
    void deletesEachBlockOfTheOwner() {
        given(findUploadedBlocksPort.findUploaded(ownerId, List.of("h1", "h2"))).willReturn(Map.of());

        service.purgeBlocks(new PurgeBlocksCommand(ownerId, List.of("h1", "h2"), decidedAt));

        then(deleteBlocksPort).should().deleteUnlessRewritten(Blocks.key(ownerId, "h1"), decidedAt);
        then(deleteBlocksPort).should().deleteUnlessRewritten(Blocks.key(ownerId, "h2"), decidedAt);
    }

    @Test
    @DisplayName("올라왔다는 기록이 살아 있는 블록은 결정 전에 쓰였어도 남긴다")
    void keepsABlockWithALiveUploadRecord() {
        given(findUploadedBlocksPort.findUploaded(ownerId, List.of("h1", "h2"))).willReturn(Map.of("h1", 4));

        service.purgeBlocks(new PurgeBlocksCommand(ownerId, List.of("h1", "h2"), decidedAt));

        then(deleteBlocksPort).should(never()).deleteUnlessRewritten(eq(Blocks.key(ownerId, "h1")), any());
        then(deleteBlocksPort).should().deleteUnlessRewritten(Blocks.key(ownerId, "h2"), decidedAt);
    }
}
