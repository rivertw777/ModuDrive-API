package com.moduDrive.file.adapter.out.client.storage;

import com.moduDrive.common.core.web.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
class StorageClientAdapterTest {

    @Mock private StorageClient storageClient;
    @InjectMocks private StorageClientAdapter adapter;

    private final UUID ownerId = UUID.randomUUID();

    @Nested
    @DisplayName("올라온 블록을 물을 때")
    class WhenAskingForUploadedBlocks {

        @Test
        void returnsTheSizesStorageAnswered() {
            given(storageClient.findUploadedBlocks(new FindUploadedBlocksRequest(ownerId, List.of("h1", "h2"))))
                    .willReturn(ApiResponse.success(Map.of("h1", 4)));

            assertThat(adapter.findUploadedBlocks(ownerId, List.of("h1", "h2"))).containsExactly(Map.entry("h1", 4));
        }

        @Test
        void treatsAnEmptyAnswerAsNothingUploaded() {
            given(storageClient.findUploadedBlocks(new FindUploadedBlocksRequest(ownerId, List.of("h1"))))
                    .willReturn(ApiResponse.success(null));

            assertThat(adapter.findUploadedBlocks(ownerId, List.of("h1"))).isEmpty();
        }
    }
}
