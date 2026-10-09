package com.moduDrive.storage.application.service;

import com.moduDrive.storage.application.port.out.ClaimStaleUploadsPort;
import com.moduDrive.storage.application.port.out.ClaimStaleUploadsPort.UploadedBlock;
import com.moduDrive.storage.application.port.out.DeleteBlocksPort;
import com.moduDrive.storage.application.port.out.FindCommittedBlocksPort;
import com.moduDrive.storage.domain.model.Blocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PurgeUncommittedUploadsServiceTest {

    @Mock private ClaimStaleUploadsPort claimStaleUploadsPort;
    @Mock private FindCommittedBlocksPort findCommittedBlocksPort;
    @Mock private DeleteBlocksPort deleteBlocksPort;
    @InjectMocks private PurgeUncommittedUploadsService service;

    private final UUID owner = UUID.randomUUID();

    @Nested
    @DisplayName("오래된 업로드가 있을 때")
    class WhenStaleUploadsAreClaimed {

        @Test
        @DisplayName("커밋되지 않은 블록만 지우고, 커밋된 블록은 file-service에 맡긴다")
        void deletesOnlyUncommittedBlocks() {
            given(claimStaleUploadsPort.claimStale(any(), anyInt()))
                    .willReturn(List.of(new UploadedBlock(owner, "h1"), new UploadedBlock(owner, "h2")));
            given(findCommittedBlocksPort.findCommitted(owner, List.of("h1", "h2"))).willReturn(Set.of("h2"));

            service.purgeUncommittedUploads();

            then(deleteBlocksPort).should().deleteUnlessRewritten(eq(Blocks.key(owner, "h1")), any());
            then(deleteBlocksPort).should(never()).deleteUnlessRewritten(eq(Blocks.key(owner, "h2")), any());
        }

        @Test
        @DisplayName("업로드 기록이 사라진 뒤(25시간)의 것만 고른다")
        void claimsOnlyPastTheUploadWindow() {
            given(claimStaleUploadsPort.claimStale(any(), anyInt())).willReturn(List.of());
            Instant before = Instant.now();

            service.purgeUncommittedUploads();

            ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
            then(claimStaleUploadsPort).should().claimStale(cutoff.capture(), eq(100));
            Duration window = Blocks.UPLOAD_TTL.plusHours(1);
            assertThat(cutoff.getValue()).isBetween(before.minus(window), Instant.now().minus(window));
        }

        @Test
        @DisplayName("file-service에 묻지 못하면 지우지 않고 넘어간다")
        void skipsDeletingWhenFileServiceIsUnreachable() {
            given(claimStaleUploadsPort.claimStale(any(), anyInt())).willReturn(List.of(new UploadedBlock(owner, "h1")));
            given(findCommittedBlocksPort.findCommitted(any(), any())).willThrow(new RuntimeException("down"));

            service.purgeUncommittedUploads();

            then(deleteBlocksPort).shouldHaveNoInteractions();
        }
    }
}
