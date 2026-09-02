package com.moduDrive.file.adapter.out.client.storage;

import com.moduDrive.file.application.port.out.PurgeStorageBlocksPort;
import com.moduDrive.file.domain.model.File.FileId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class StorageServiceClientAdapter implements PurgeStorageBlocksPort {

    private final StorageServiceClient storageServiceClient;

    @Override
    public void purgeBlocks(FileId fileId, UUID ownerId) {
        storageServiceClient.purgeStoredFile(fileId.value().toString(), ownerId.toString());
    }
}
