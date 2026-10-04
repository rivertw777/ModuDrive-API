package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.file.application.port.out.FindNamespacePort;
import com.moduDrive.file.application.port.out.SaveNamespacePort;
import com.moduDrive.file.domain.model.Namespace;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@PersistenceAdapter
@RequiredArgsConstructor
class NamespacePersistenceAdapter implements SaveNamespacePort, FindNamespacePort {

    private final SpringDataNamespaceRepository namespaceRepository;
    private final FileMapper fileMapper;

    @Override
    public Namespace saveNamespace(Namespace namespace) {
        NamespaceJpaEntity entity = new NamespaceJpaEntity(namespace.getUserId(), namespace.getRootPath(), namespace.getQuotaBytes());
        return fileMapper.mapNamespaceToDomain(namespaceRepository.save(entity));
    }

    @Override
    public Optional<Namespace> findByUserId(NamespaceUserId userId) {
        return namespaceRepository.findByUserId(userId.value())
                .map(fileMapper::mapNamespaceToDomain);
    }
}
