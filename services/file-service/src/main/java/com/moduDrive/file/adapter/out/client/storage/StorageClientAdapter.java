package com.moduDrive.file.adapter.out.client.storage;

import com.moduDrive.file.application.port.out.FindUploadedBlocksPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class StorageClientAdapter implements FindUploadedBlocksPort {

    private final StorageClient storageClient;

    @Override
    public Map<String, Integer> findUploadedBlocks(UUID ownerId, Collection<String> hashes) {
        Map<String, Integer> uploaded = storageClient
                .findUploadedBlocks(new FindUploadedBlocksRequest(ownerId, List.copyOf(hashes))).getData();
        return uploaded == null ? Map.of() : uploaded;
    }
}
