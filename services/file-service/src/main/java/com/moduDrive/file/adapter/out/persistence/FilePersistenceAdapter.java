package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.out.*;
import com.moduDrive.file.domain.model.*;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare.FileShareId;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.domain.model.Namespace.NamespaceUserId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
        SaveFileSharePort, FindFileSharePort, DeleteFileSharePort,
        SaveFileAccessPort, FindFileAccessPort {

    private final SpringDataNamespaceRepository namespaceRepository;
    private final SpringDataFileRepository fileRepository;
    private final SpringDataFileVersionRepository fileVersionRepository;
    private final SpringDataFileShareRepository fileShareRepository;
    private final SpringDataFileAccessRepository fileAccessRepository;
    private final FileMapper fileMapper;
    private final RolePermissionPersistenceAdapter rolePermissionPersistenceAdapter;

    @Override
    public Namespace saveNamespace(Namespace namespace) {
        NamespaceJpaEntity entity = new NamespaceJpaEntity(namespace.getUserId(), namespace.getRootPath(), namespace.getQuotaBytes());
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

        entity.applyChanges(file.getName(), file.getPath(), file.getCurrentVersionId(), file.getFileSize(),
                file.getStatus(), file.isFavorite(), file.getAccessScope(), file.getLinkToken(), file.getLinkRole());

        return fileMapper.mapFileToDomain(fileRepository.save(entity));
    }

    @Override
    public void deleteFile(FileId fileId) {
        fileRepository.deleteById(fileId.value());
    }

    @Override
    public Optional<File> findById(FileId fileId) {
        return fileRepository.findById(fileId.value())
                .map(fileMapper::mapFileToDomain);
    }

    @Override
    public Optional<File> findByLinkToken(UUID linkToken) {
        return fileRepository.findByLinkToken(linkToken)
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
    public List<File> findByNamespaceIdAndPathStartingWith(NamespaceId namespaceId, String pathPrefix) {
        return fileRepository
                .findSubtreeByNamespaceIdAndPathPrefix(namespaceId.value(), pathPrefix, escapeLikePattern(pathPrefix))
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    /** Escapes LIKE metacharacters (\, %, _) so a directory name containing them can't widen
     * the subtree-prefix match beyond its own descendants. Pair with the query's {@code escape '\'}. */
    private static String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public List<File> findByNamespaceIdAndStatus(NamespaceId namespaceId, FileStatus status) {
        return fileRepository
                .findByNamespaceIdAndStatus(namespaceId.value(), status)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<File> findByNamespaceIdAndFavorite(NamespaceId namespaceId) {
        return fileRepository
                .findByNamespaceIdAndFavoriteTrueAndStatusNot(namespaceId.value(), FileStatus.DELETED)
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
    public List<File> findByNamespaceId(NamespaceId namespaceId) {
        return fileRepository
                .findByNamespaceIdAndDirectoryFalseAndStatusNot(namespaceId.value(), FileStatus.DELETED)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long sumFileSizeByNamespaceId(NamespaceId namespaceId) {
        return fileRepository.sumFileSizeByNamespaceId(namespaceId.value());
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
        if (fileShare.getId() == null) {
            FileShareJpaEntity entity = new FileShareJpaEntity(
                    fileShare.getFileId(), fileShare.getOwnerId(),
                    fileShare.getSharedWithUserId(), rolePermissionPersistenceAdapter.findRoleId(fileShare.getRole()),
                    fileShare.getToken(), fileShare.getGranteeEmail()
            );
            try {
                return fileMapper.mapFileShareToDomain(fileShareRepository.save(entity));
            } catch (DataIntegrityViolationException e) {
                // The app-layer existsBy check in ShareFileService is best-effort against a
                // concurrent duplicate invite; the DB unique constraint is what actually closes
                // that race, so translate its violation into the same business error instead of
                // letting a raw constraint-violation message leak out as a 500.
                throw new BusinessException(FileExceptionCase.FILE_SHARE_ALREADY_EXISTS);
            }
        }

        FileShareJpaEntity entity = fileShareRepository.findById(fileShare.getId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_SHARE_NOT_FOUND));
        entity.applyGrantedRoleId(rolePermissionPersistenceAdapter.findRoleId(fileShare.getRole()));

        return fileMapper.mapFileShareToDomain(fileShareRepository.save(entity));
    }

    @Override
    public void deleteFileShare(FileShareId shareId) {
        fileShareRepository.deleteById(shareId.value());
    }

    @Override
    public boolean existsByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId) {
        return fileShareRepository.existsByFileIdAndSharedWithUserId(fileId.value(), sharedWithUserId);
    }

    @Override
    public Optional<FileShare> findByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId) {
        return fileShareRepository.findByFileIdAndSharedWithUserId(fileId.value(), sharedWithUserId)
                .map(fileMapper::mapFileShareToDomain);
    }

    @Override
    public boolean existsByFileIdAndGranteeEmail(FileId fileId, String granteeEmail) {
        return fileShareRepository.existsByFileIdAndGranteeEmail(fileId.value(), granteeEmail);
    }

    @Override
    public Optional<FileShare> findByToken(UUID token) {
        return fileShareRepository.findByToken(token)
                .map(fileMapper::mapFileShareToDomain);
    }

    @Override
    public Optional<FileShare> findByShareId(FileShareId shareId) {
        return fileShareRepository.findById(shareId.value())
                .map(fileMapper::mapFileShareToDomain);
    }

    @Override
    public List<FileShare> findByFileId(FileId fileId) {
        return fileShareRepository.findByFileId(fileId.value())
                .stream()
                .map(fileMapper::mapFileShareToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<FileShare> findBySharedWithUserId(UUID sharedWithUserId) {
        return fileShareRepository.findBySharedWithUserId(sharedWithUserId)
                .stream()
                .map(fileMapper::mapFileShareToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void recordAccess(FileAccess fileAccess) {
        FileAccessJpaEntity entity = fileAccessRepository
                .findByUserIdAndFileId(fileAccess.getUserId(), fileAccess.getFileId())
                .map(existing -> {
                    existing.touch(fileAccess.getAccessedAt());
                    return existing;
                })
                .orElseGet(() -> new FileAccessJpaEntity(
                        fileAccess.getUserId(), fileAccess.getFileId(), fileAccess.getAccessedAt()));
        // saveAndFlush (not save): forces the uk_file_access_user_file violation from a
        // concurrent insert-race to surface here, synchronously, instead of at commit time
        // after the caller (RecordFileAccessService) has already returned — that's what lets
        // its try/catch actually catch it instead of the exception leaking into the response.
        fileAccessRepository.saveAndFlush(entity);
    }

    @Override
    public List<FileAccess> findByUserIdOrderByAccessedAtDesc(UUID userId, int limit) {
        return fileAccessRepository.findByUserIdOrderByAccessedAtDesc(userId, PageRequest.of(0, limit))
                .stream()
                .map(fileMapper::mapFileAccessToDomain)
                .collect(Collectors.toList());
    }
}
