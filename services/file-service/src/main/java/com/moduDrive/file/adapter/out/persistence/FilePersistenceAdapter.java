package com.moduDrive.file.adapter.out.persistence;

import com.moduDrive.common.core.annotation.PersistenceAdapter;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.file.application.port.in.usecase.DirectoryPage;
import com.moduDrive.file.application.port.out.*;
import com.moduDrive.file.domain.model.*;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;
import com.moduDrive.file.exception.FileExceptionCase;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@PersistenceAdapter
@RequiredArgsConstructor
/** The File aggregate — files/folders and their versions. Share and favorite rows are other
 * aggregates' tables, but a purge still has to clear them (see cascadeDeleteAttachments). */
class FilePersistenceAdapter implements SaveFilePort, FindFilePort, SaveFileVersionPort, FindFileVersionsPort {

    private final SpringDataFileRepository fileRepository;
    private final SpringDataFileVersionRepository fileVersionRepository;
    private final SpringDataFileShareRepository fileShareRepository;
    private final SpringDataFileFavoriteRepository fileFavoriteRepository;
    private final FileMapper fileMapper;

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

        // deletedAt isn't passed: it is only ever stamped by a purge (markPurged), never by a
        // domain-driven save.
        entity.applyChanges(file.getName(), file.getPath(), file.getCurrentVersionId(), file.getFileSize(),
                file.getStatus(), file.getAccessScope(), file.getLinkRole(),
                file.getTrashedAt());

        // Same conflict, different door: rename/move/restore land here, and none of their callers
        // pre-check the destination slot either (e.g. RestoreFileService can restore a file back
        // onto a slot a later upload already took over) — this used to throw a raw
        // DataIntegrityViolationException out through the transaction commit as a 500.
        return fileMapper.mapFileToDomain(saveAndTranslateSlotConflict(entity));
    }

    @Override
    public List<File> saveNewFiles(List<File> files) {
        List<FileJpaEntity> entities = files.stream()
                .map(file -> new FileJpaEntity(file.getNamespaceId(), file.getName(), file.getPath(),
                        file.getOwnerId(), file.getStatus(), file.isDirectory()))
                .toList();
        try {
            List<FileJpaEntity> saved = fileRepository.saveAll(entities);
            // One flush for the whole list, still inside this try — same reason saveAndFlush is
            // used below: the slot violation must surface here to be translated.
            fileRepository.flush();
            return saved.stream().map(fileMapper::mapFileToDomain).toList();
        } catch (DataIntegrityViolationException e) {
            if (isActiveSlotConflict(e)) {
                throw new BusinessException(FileExceptionCase.FILE_ALREADY_EXISTS);
            }
            throw e;
        }
    }

    /** saveAndFlush (not save): forces uk_file_namespace_path_active_name's violation to surface
     * here, synchronously — a plain save() only persists to the session and defers the actual
     * insert/update to a later, uncontrolled flush, which this catch would never see. Same
     * reasoning as FileAccessPersistenceAdapter.recordAccess. Only that specific constraint is translated to a business
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

    /** Hard delete — no caller in the trash flow anymore (purgeFile keeps a tombstone), kept for
     * a future job that clears out tombstones older than the recovery window. */
    @Override
    public void deleteFile(FileId fileId) {
        cascadeDeleteAttachments(fileId);
        fileRepository.deleteById(fileId.value());
    }

    @Override
    public void purgeFile(FileId fileId, UUID deletedBy) {
        // Tombstone purge: drop everything that costs storage, keep the metadata row with
        // deletedAt stamped. markPurged is a plain UPDATE (not a JPA save) so it doesn't bump
        // updatedAt — DirectoryCascader.purge's sibling check relies on trash-time timestamps
        // staying put.
        cascadeDeleteAttachments(fileId);
        fileRepository.markPurged(fileId.value(), LocalDateTime.now(), deletedBy);
    }

    // The file's versions would otherwise dangle forever, pointing at S3 prefixes that
    // FilePurger/DirectoryCascader already deleted the blocks under. Its share and favorite rows
    // likewise — no FK cascade, so a grant or a star on a purged file/folder would linger and
    // only ever get filtered out at read time (ListSharedWithMeService / ListFavoritesService).
    private void cascadeDeleteAttachments(FileId fileId) {
        fileVersionRepository.deleteByFileId(fileId.value());
        fileShareRepository.deleteByFileId(fileId.value());
        fileFavoriteRepository.deleteByFileId(fileId.value());
    }

    @Override
    public Optional<File> findById(FileId fileId) {
        return fileRepository.findById(fileId.value())
                .map(fileMapper::mapFileToDomain);
    }

    @Override
    public List<File> findByNamespaceIdAndPath(NamespaceId namespaceId, String path) {
        return fileRepository
                .findByNamespaceIdAndPathAndStatusNotIn(namespaceId.value(), path, FileStatus.REMOVED)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public DirectoryPage findDirectoryPage(NamespaceId namespaceId, String path, DirectorySort sort,
                                           String cursor, int limit) {
        Window<FileJpaEntity> window = fileRepository.findBy(
                directoryListingSpec(namespaceId.value(), path),
                query -> query
                        .sortBy(directoryListingSort(sort))
                        .limit(limit)
                        .scroll(DirectoryCursorCodec.decode(cursor, sort)));

        List<File> content = window.getContent().stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());

        String nextCursor = !window.isEmpty() && window.hasNext()
                ? DirectoryCursorCodec.encode(window.positionAt(window.size() - 1), sort)
                : null;

        return new DirectoryPage(content, nextCursor, window.hasNext());
    }

    private static Specification<FileJpaEntity> directoryListingSpec(UUID namespaceId, String path) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("namespaceId"), namespaceId),
                cb.equal(root.get("path"), path),
                cb.not(root.get("status").in(FileStatus.REMOVED)));
    }

    /** Directories first (regardless of the chosen field), then the field, then the entity id
     * itself (appended by Spring Data) as the final keyset tie-breaker. Every column used here is
     * non-null — a keyset scroll drops and repeats rows if it keysets on a null value. */
    private static Sort directoryListingSort(DirectorySort sort) {
        Sort.Order directoriesFirst = Sort.Order.desc("directory");
        return switch (sort) {
            case NAME_ASC -> Sort.by(directoriesFirst, Sort.Order.asc("name"));
            case NAME_DESC -> Sort.by(directoriesFirst, Sort.Order.desc("name"));
            case MODIFIED_ASC -> Sort.by(directoriesFirst, Sort.Order.asc("updatedAt"));
            case MODIFIED_DESC -> Sort.by(directoriesFirst, Sort.Order.desc("updatedAt"));
        };
    }

    @Override
    public Optional<File> findActiveByNamespaceIdAndPathAndName(NamespaceId namespaceId, String path, String name) {
        return fileRepository
                .findByNamespaceIdAndPathAndNameAndStatusNotIn(namespaceId.value(), path, name, FileStatus.REMOVED)
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
    public List<File> findTrashedNotPurged(NamespaceId namespaceId) {
        return fileRepository
                .findByNamespaceIdAndStatus(namespaceId.value(), FileStatus.TRASHED)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<File> findByNamespaceIdAndNameContaining(NamespaceId namespaceId, String query) {
        return fileRepository
                .findByNamespaceIdAndNameContainingIgnoreCaseAndStatusNotIn(namespaceId.value(), query, FileStatus.REMOVED)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<File> findByNamespaceId(NamespaceId namespaceId) {
        return fileRepository
                .findByNamespaceIdAndDirectoryFalseAndStatusNotIn(namespaceId.value(), FileStatus.REMOVED)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long sumFileSizeByNamespaceId(NamespaceId namespaceId) {
        return fileRepository.sumFileSizeByNamespaceId(namespaceId.value());
    }

    @Override
    public List<File> findExpiredTrash(LocalDateTime cutoff) {
        return fileRepository
                .findByStatusAndTrashedAtBefore(FileStatus.TRASHED, cutoff)
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
    public List<FileVersion> findAllByIds(Collection<UUID> versionIds) {
        return fileVersionRepository.findAllById(versionIds).stream()
                .map(fileMapper::mapFileVersionToDomain)
                .toList();
    }

    @Override
    public Optional<FileVersion> findByS3Path(String s3Path) {
        return fileVersionRepository.findByS3Path(s3Path).map(fileMapper::mapFileVersionToDomain);
    }

    @Override
    public List<FileVersion> findAllByFileId(FileId fileId) {
        return fileVersionRepository.findByFileId(fileId.value()).stream()
                .map(fileMapper::mapFileVersionToDomain)
                .toList();
    }

    @Override
    public List<FileVersion> findByFileIdOrderByCreatedAtDesc(FileId fileId, int limit) {
        return fileVersionRepository
                .findByFileIdOrderByCreatedAtDesc(fileId.value(), PageRequest.of(0, limit))
                .stream()
                .map(fileMapper::mapFileVersionToDomain)
                .collect(Collectors.toList());
    }

}
