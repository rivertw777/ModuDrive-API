package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.*;
import com.moduDrive.file.domain.model.*;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@PersistenceAdapter
@RequiredArgsConstructor
class FilePersistenceAdapter implements
        SaveNamespacePort, FindNamespacePort,
        SaveFilePort, FindFilePort,
        SaveFileVersionPort, FindFileVersionsPort,
        SaveFileSharePort, FindFileSharePort {

    private final SpringDataNamespaceRepository namespaceRepository;
    private final SpringDataFileRepository fileRepository;
    private final SpringDataFileVersionRepository fileVersionRepository;
    private final SpringDataFileShareRepository fileShareRepository;
    private final FileMapper fileMapper;

    @Override
    public Namespace saveNamespace(Namespace namespace) {
        NamespaceJpaEntity entity = new NamespaceJpaEntity(namespace.getUserId(), namespace.getRootPath());
        return fileMapper.mapNamespaceToDomain(namespaceRepository.save(entity));
    }

    @Override
    public boolean existsByUserId(NamespaceUserId userId) {
        return namespaceRepository.existsByUserId(userId.value());
    }

    @Override
    public Optional<Namespace> findByUserId(NamespaceUserId userId) {
        return namespaceRepository.findByUserId(userId.value())
                .map(fileMapper::mapNamespaceToDomain);
    }

    @Override
    public File saveFile(File file) {
        if (file.getId() == null) {
            FileJpaEntity entity = new FileJpaEntity(
                    file.getNamespaceId(), file.getName(), file.getPath(),
                    file.getOwnerId(), file.getStatus(), file.isDirectory()
            );
            return fileMapper.mapFileToDomain(fileRepository.save(entity));
        }

        FileJpaEntity entity = fileRepository.findById(file.getId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));

        switch (file.getStatus()) {
            case UPLOADED -> entity.markUploaded(file.getCurrentVersionId(), file.getFileSize());
            case DELETED -> entity.softDelete();
            default -> throw new IllegalStateException("Unexpected status update: " + file.getStatus());
        }

        return fileMapper.mapFileToDomain(fileRepository.save(entity));
    }

    @Override
    public Optional<File> findById(FileId fileId) {
        return fileRepository.findById(fileId.value())
                .map(fileMapper::mapFileToDomain);
    }

    @Override
    public List<File> findByNamespaceIdAndPath(NamespaceId namespaceId, String path) {
        return fileRepository
                .findByNamespaceIdAndPathAndStatusNot(namespaceId.value(), path, FileStatus.DELETED)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<File> findByNamespaceIdAndNameContaining(NamespaceId namespaceId, String query) {
        return fileRepository
                .findByNamespaceIdAndNameContainingIgnoreCaseAndStatusNot(namespaceId.value(), query, FileStatus.DELETED)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public FileVersion saveFileVersion(FileVersion fileVersion) {
        FileVersionJpaEntity entity = new FileVersionJpaEntity(
                fileVersion.getFileId(), fileVersion.getFileSize(),
                fileVersion.getBlockCount(), fileVersion.getS3Path()
        );
        return fileMapper.mapFileVersionToDomain(fileVersionRepository.save(entity));
    }

    @Override
    public List<FileVersion> findByFileIdOrderByCreatedAtDesc(FileId fileId, int limit) {
        return fileVersionRepository
                .findByFileIdOrderByCreatedAtDesc(fileId.value(), PageRequest.of(0, limit))
                .stream()
                .map(fileMapper::mapFileVersionToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public FileShare saveFileShare(FileShare fileShare) {
        FileShareJpaEntity entity = new FileShareJpaEntity(
                fileShare.getFileId(), fileShare.getOwnerId(),
                fileShare.getSharedWithUserId(), fileShare.getPermission()
        );
        return fileMapper.mapFileShareToDomain(fileShareRepository.save(entity));
    }

    @Override
    public boolean existsByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId) {
        return fileShareRepository.existsByFileIdAndSharedWithUserId(fileId.value(), sharedWithUserId);
    }
}
