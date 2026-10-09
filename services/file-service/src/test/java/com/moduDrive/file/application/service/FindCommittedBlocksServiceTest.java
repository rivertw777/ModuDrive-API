package com.moduDrive.file.application.service;

import com.moduDrive.file.application.port.out.FindCommittedBlocksPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FindCommittedBlocksServiceTest {

    @Mock private FindCommittedBlocksPort findCommittedBlocksPort;
    @InjectMocks private FindCommittedBlocksService service;

    @Test
    void returnsTheCommittedHashes() {
        UUID ownerId = UUID.randomUUID();
        given(findCommittedBlocksPort.findCommittedHashes(ownerId, List.of("h1", "h2"))).willReturn(Set.of("h1"));

        assertThat(service.findCommittedBlocks(ownerId, List.of("h1", "h2"))).containsExactly("h1");
    }
}
