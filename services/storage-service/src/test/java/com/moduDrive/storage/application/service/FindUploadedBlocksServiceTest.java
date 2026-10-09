package com.moduDrive.storage.application.service;

import com.moduDrive.storage.application.port.out.FindUploadedBlocksPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FindUploadedBlocksServiceTest {

    @Mock private FindUploadedBlocksPort findUploadedBlocksPort;
    @InjectMocks private FindUploadedBlocksService service;

    @Test
    void returnsTheUploadedSizes() {
        UUID ownerId = UUID.randomUUID();
        given(findUploadedBlocksPort.findUploaded(ownerId, List.of("h1", "h2"))).willReturn(Map.of("h1", 4));

        assertThat(service.findUploadedBlocks(ownerId, List.of("h1", "h2"))).containsExactly(Map.entry("h1", 4));
    }
}
