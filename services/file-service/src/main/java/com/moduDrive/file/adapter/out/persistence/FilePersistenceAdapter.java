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

import java.time.LocalDateTime;
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

    // ponytail: hardcoded TTL, not a config value yet — bump to a @Value if a real need to tune
    // it per deployment shows up.
    private static final int GUEST_SHARE_TOKEN_TTL_DAYS = 7;

    private final SpringDataNamespaceRepository namespaceRepository;
    private final SpringDataFileRepository fileRepository;
    private final SpringDataFileVersionRepository fileVersionRepository;
    private final SpringDataFileShareRepository fileShareRepository;
    private final SpringDataFileAccessRepository fileAccessRepository;
    private final FileMapper fileMapper;

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
            // UploadFileMetadataService/CreateDirectoryService's own same-name pre-check (where
            // they have one) isn't atomic with this insert, so a concurrent request for the same
            // new name can still slip through between them.
            return fileMapper.mapFileToDomain(saveAndTranslateSlotConflict(entity));
        }

        FileJpaEntity entity = fileRepository.findById(file.getId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));

        entity.applyChanges(file.getName(), file.getPath(), file.getCurrentVersionId(), file.getFileSize(),
                file.getStatus(), file.isFavorite(), file.getAccessScope(), file.getLinkToken(), file.getLinkRole());

        // Same conflict, different door: rename/move/restore land here, and none of their callers
        // pre-check the destination slot either (e.g. RestoreFileService can restore a file back
        // onto a slot a later upload already took over) — this used to throw a raw
        // DataIntegrityViolationException out through the transaction commit as a 500.
        return fileMapper.mapFileToDomain(saveAndTranslateSlotConflict(entity));
    }

    /** saveAndFlush (not save): forces uk_file_namespace_path_active_name's violation to surface
     * here, synchronously — a plain save() only persists to the session and defers the actual
     * insert/update to a later, uncontrolled flush, which this catch would never see. Same
     * reasoning as recordAccess below. Only that specific constraint is translated to a business
     * error — a NOT NULL/FK/other integrity violation is a real bug and should surface as-is
     * rather than being reported to the caller as "already exists". */
    private FileJpaEntity saveAndTranslateSlotConflict(FileJpaEntity entity) {
        try {
            return fileRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            if (isActiveSlotConflict(e)) {
                throw new BusinessException(FileExceptionCase.FILE_ALREADY_EXISTS);
            }
            throw e;
        }
    }

    private static boolean isActiveSlotConflict(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        return cause.getMessage() != null
                && cause.getMessage().toLowerCase().contains("uk_file_namespace_path_active_name");
    }

    @Override
    public void deleteFile(FileId fileId) {
        // A purged file's versions would otherwise dangle forever, pointing at S3 prefixes that
        // FilePurger/DirectoryCascader already deleted the blocks under.
        fileVersionRepository.deleteByFileId(fileId.value());
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
    public Optional<File> findActiveByNamespaceIdAndPathAndName(NamespaceId namespaceId, String path, String name) {
        return fileRepository
                .findByNamespaceIdAndPathAndNameAndStatusNot(namespaceId.value(), path, name, FileStatus.DELETED)
                .map(fileMapper::mapFileToDomain);
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
    public List<File> findByStatusAndUpdatedAtBefore(FileStatus status, LocalDateTime cutoff) {
        return fileRepository.findByStatusAndUpdatedAtBefore(status, cutoff)
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
        if (fileShare.getId() == null) {
            FileShareJpaEntity entity = new FileShareJpaEntity(
                    fileShare.getFileId(), fileShare.getOwnerId(),
                    fileShare.getSharedWithUserId(), fileShare.getRole(),
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
        entity.applyGrantedRole(fileShare.getRole());
        // Only a pending→claimed transition (entity still null, incoming now set) should touch
        // sharedWithUserId here — an ordinary role update on an already-granted share must not
        // re-run applyClaim's side effect of clearing token/granteeEmail (a no-op for those rows,
        // but the guard keeps this branch doing only what its caller — ClaimPendingFileSharesService
        // — asks of it). ClaimPendingFileSharesService pre-checks for a colliding grant before
        // calling this, so no DataIntegrityViolationException is expected here.
        if (entity.getSharedWithUserId() == null && fileShare.getSharedWithUserId() != null) {
            entity.applyClaim(fileShare.getSharedWithUserId());
        }

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
        LocalDateTime cutoff = LocalDateTime.now().minusDays(GUEST_SHARE_TOKEN_TTL_DAYS);
        return fileShareRepository.findByTokenAndCreatedAtAfter(token, cutoff)
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
    public List<FileShare> findPendingByGranteeEmail(String granteeEmail) {
        return fileShareRepository.findByGranteeEmailAndSharedWithUserIdIsNull(granteeEmail)
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
