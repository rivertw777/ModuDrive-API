package com.moduDrive.file.adapter.out.client.storage;

import com.moduDrive.common.core.web.ApiResponse;
import com.moduDrive.file.domain.model.File.FileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class StorageServiceClientAdapterTest {

    @Mock private StorageServiceClient storageServiceClient;
    @InjectMocks private StorageServiceClientAdapter adapter;

    @Nested
    @DisplayName("블록 삭제를 요청할 때")
    class WhenPurgingBlocks {

        @Test
        void forwardsFileIdAndOwnerIdAsStrings() {
            FileId fileId = new FileId(UUID.randomUUID());
            UUID ownerId = UUID.randomUUID();
            given(storageServiceClient.purgeStoredFile(fileId.value().toString(), ownerId.toString()))
                    .willReturn(ApiResponse.success());

            adapter.purgeBlocks(fileId, ownerId);

            then(storageServiceClient).should()
                    .purgeStoredFile(fileId.value().toString(), ownerId.toString());
        }
    }
}
