package com.moduDrive.member.adapter.out.client.file;

import com.moduDrive.common.api.dto.namespace.CreateNamespaceRequest;
import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.member.application.port.out.CreateNamespacePort;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

@PersistenceAdapter
@RequiredArgsConstructor
class FileServiceClientAdapter implements CreateNamespacePort {

    private final FileServiceClient fileServiceClient;

    @Override
    public void createNamespace(UUID userId) {
        fileServiceClient.createNamespace(new CreateNamespaceRequest(userId));
    }
}
