package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.ClaimUnreferencedBlocksPort;
import com.moduDrive.file.application.port.out.ClaimUnreferencedBlocksPort.UnreferencedBlock;
import com.moduDrive.file.application.port.out.PurgeStorageBlocksPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PurgeUnreferencedBlocksServiceTest {

    @Mock private ClaimUnreferencedBlocksPort claimUnreferencedBlocksPort;
    @Mock private PurgeStorageBlocksPort purgeStorageBlocksPort;
    @InjectMocks private PurgeUnreferencedBlocksService service;

    private final UUID owner1 = UUID.randomUUID();
    private final UUID owner2 = UUID.randomUUID();

    @Nested
    @DisplayName("유예 기간이 지난 블록이 있을 때")
    class WhenBlocksAreClaimed {

        @Test
        @DisplayName("소유자별로 묶어 같은 결정 시각으로 삭제를 요청하고 개수를 돌려준다")
        void requestsPurgePerOwner() {
            given(claimUnreferencedBlocksPort.claimUnreferenced(any(), anyInt())).willReturn(List.of(
                    new UnreferencedBlock(owner1, "h1"), new UnreferencedBlock(owner2, "h2"),
                    new UnreferencedBlock(owner1, "h3")));

            int claimed = service.purgeUnreferencedBlocks();

            assertThat(claimed).isEqualTo(3);
            ArgumentCaptor<Instant> decidedAt = ArgumentCaptor.forClass(Instant.class);
            then(purgeStorageBlocksPort).should().purgeBlocks(eq(owner1), eq(List.of("h1", "h3")), decidedAt.capture());
            then(purgeStorageBlocksPort).should().purgeBlocks(eq(owner2), eq(List.of("h2")), eq(decidedAt.getValue()));
        }

        @Test
        @DisplayName("24시간 전보다 먼저 참조가 끊긴 블록만 고른다")
        void claimsOnlyPastTheGracePeriod() {
            given(claimUnreferencedBlocksPort.claimUnreferenced(any(), anyInt())).willReturn(List.of());
            LocalDateTime before = LocalDateTime.now().minusHours(24);

            service.purgeUnreferencedBlocks();

            ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
            then(claimUnreferencedBlocksPort).should().claimUnreferenced(cutoff.capture(), eq(500));
            assertThat(cutoff.getValue()).isBetween(before, LocalDateTime.now().minusHours(24));
        }
    }

    @Nested
    @DisplayName("지울 블록이 없을 때")
    class WhenNothingIsClaimed {

        @Test
        void requestsNothing() {
            given(claimUnreferencedBlocksPort.claimUnreferenced(any(), anyInt())).willReturn(List.of());

            assertThat(service.purgeUnreferencedBlocks()).isZero();
            then(purgeStorageBlocksPort).shouldHaveNoInteractions();
        }
    }
}
