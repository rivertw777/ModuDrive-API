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
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Files, folders and their versions. A purge also clears the file's share and favorite rows
 * (see deleteAttachments). */
@PersistenceAdapter
@RequiredArgsConstructor
class FilePersistenceAdapter implements SaveFilePort, FindFilePort, SaveFileVersionPort, FindFileVersionsPort,
        DeleteFileVersionsPort {

    private final SpringDataFileRepository fileRepository;
    private final SpringDataFileVersionRepository fileVersionRepository;
    private final SpringDataFileShareRepository fileShareRepository;
    private final SpringDataFileFavoriteRepository fileFavoriteRepository;
    private final SpringDataNamespaceRepository namespaceRepository;
    private final FileMapper fileMapper;
    private final AuditorAware<UUID> auditorAware;

    @Override
    public File saveFile(File file) {
        return saveFiles(List.of(file)).getFirst();
    }

    @Override
    public List<File> saveFiles(List<File> files) {
        List<FileJpaEntity> entities = files.stream().map(this::toEntity).toList();
        // Callers' same-name pre-checks aren't atomic with the insert; the slot constraint is.
        // Rename/move/restore can collide too (a restore onto a slot a later upload took).
        return saveAndTranslateSlotConflict(entities).stream().map(fileMapper::mapFileToDomain).toList();
    }

    private FileJpaEntity toEntity(File file) {
        if (file.getId() == null) {
            return new FileJpaEntity(
                    file.getNamespaceId(), file.getName(), file.getPath(),
                    file.getOwnerId(), file.getStatus(), file.isDirectory()
            );
        }

        FileJpaEntity entity = fileRepository.findById(file.getId())
                .orElseThrow(() -> new BusinessException(FileExceptionCase.FILE_NOT_FOUND));

        // deletedAt isn't passed: only a purge (markPurged) stamps it.
        entity.applyChanges(file.getName(), file.getPath(), file.getCurrentVersionId(), file.getFileSize(),
                file.getStatus(), file.getAccessScope(), file.getLinkRole(),
                file.getTrashedAt());
        return entity;
    }

    /** saveAllAndFlush, so a uk_file_namespace_path_active_name violation surfaces inside this catch
     * rather than at a later flush. Only that constraint becomes FILE_ALREADY_EXISTS; any other
     * integrity violation is a bug and surfaces as is. */
    private List<FileJpaEntity> saveAndTranslateSlotConflict(List<FileJpaEntity> entities) {
        try {
            return fileRepository.saveAllAndFlush(entities);
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
    public void purgeFile(FileId fileId, UUID deletedBy) {
        purgeFiles(List.of(fileId), deletedBy);
    }

    @Override
    public void purgeFiles(Collection<FileId> fileIds, UUID deletedBy) {
        if (fileIds.isEmpty()) {
            return;
        }
        // Only rows still TRASHED, and the rest only for those — a file restored meanwhile keeps its
        // versions, shares and favorites, and a second purge of the same file frees nothing twice.
        // The callers already lock and re-check what they pass; this keeps that true per row anyway.
        UUID[] ids = fileRepository.lockTrashedIds(fileIds.stream().map(FileId::value).toArray(UUID[]::new))
                .toArray(UUID[]::new);
        if (ids.length == 0) {
            return;
        }
        // markPurged is a plain UPDATE, not a JPA save, so updatedAt isn't bumped.
        fileRepository.markPurged(ids, LocalDateTime.now(), deletedBy);
        deleteAttachments(ids);
        // In the same transaction as the tombstones, last, like the commit's usage update: one
        // UPDATE of the drive's row for the whole batch.
        namespaceRepository.releaseUsedBytesOf(ids);
    }

    // No FK cascades: the versions (whose block references the caller already released), shares
    // and favorites would otherwise linger. One bulk delete per table.
    private void deleteAttachments(UUID[] fileIds) {
        fileVersionRepository.deleteBlocklistsByFileIds(fileIds);
        fileVersionRepository.deleteByFileIds(fileIds);
        fileShareRepository.deleteByFileIds(fileIds);
        fileFavoriteRepository.deleteByFileIds(fileIds);
    }

    @Override
    public void moveSubtree(NamespaceId namespaceId, String prefix, String newPrefix) {
        lockSubtree(namespaceId, prefix);
        translateSlotConflict(() -> fileRepository.moveSubtree(namespaceId.value(), prefix,
                escapeLikePattern(prefix), newPrefix, LocalDateTime.now(), currentAuditor()));
    }

    @Override
    public void trashSubtree(NamespaceId namespaceId, String prefix, LocalDateTime trashedAt) {
        lockSubtree(namespaceId, prefix);
        fileRepository.trashSubtree(namespaceId.value(), prefix, escapeLikePattern(prefix), trashedAt,
                LocalDateTime.now(), currentAuditor());
    }

    @Override
    public void restoreSubtree(NamespaceId namespaceId, String prefix) {
        lockSubtree(namespaceId, prefix);
        // Restoring re-occupies the rows' name slots, so it can collide like a single restore does.
        translateSlotConflict(() -> fileRepository.restoreSubtree(namespaceId.value(), prefix,
                escapeLikePattern(prefix), LocalDateTime.now(), currentAuditor()));
    }

    // Locked top-down before the bulk UPDATE: the UPDATE alone would lock rows in whatever order its
    // scan finds them, and could then deadlock against a commit locking the same folders top-down.
    private void lockSubtree(NamespaceId namespaceId, String prefix) {
        fileRepository.lockSubtreeIds(namespaceId.value(), prefix, escapeLikePattern(prefix));
    }

    // A bulk UPDATE bypasses JPA auditing, so it stamps updated_by itself from the same source.
    private UUID currentAuditor() {
        return auditorAware.getCurrentAuditor().orElse(null);
    }

    private static void translateSlotConflict(Runnable update) {
        try {
            update.run();
        } catch (DataIntegrityViolationException e) {
            if (isActiveSlotConflict(e)) {
                throw new BusinessException(FileExceptionCase.FILE_ALREADY_EXISTS);
            }
            throw e;
        }
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
    public Set<String> findActiveNamesByNamespaceIdAndPath(NamespaceId namespaceId, String path) {
        return new HashSet<>(fileRepository.findNamesByNamespaceIdAndPathAndStatusNotIn(namespaceId.value(), path, FileStatus.REMOVED));
    }

    @Override
    public List<File> findActiveByNamespaceIdAndPathAndNames(NamespaceId namespaceId, String path, Collection<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        return fileRepository
                .findByNamespaceIdAndPathAndNameInAndStatusNotIn(namespaceId.value(), path, names, FileStatus.REMOVED)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .toList();
    }

    @Override
    public Optional<File> lockActiveByNamespaceIdAndPathAndName(NamespaceId namespaceId, String path, String name) {
        return fileRepository
                .lockByNamespaceIdAndPathAndNameAndStatusNotIn(namespaceId.value(), path, name, FileStatus.REMOVED)
                .map(fileMapper::mapFileToDomain);
    }

    @Override
    public List<File> lockActiveByNamespaceIdAndPathAndNames(NamespaceId namespaceId, String path, Collection<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        return fileRepository
                .lockByNamespaceIdAndPathAndNameInAndStatusNotIn(namespaceId.value(), path, names, FileStatus.REMOVED)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .toList();
    }

    @Override
    public Optional<File> lockById(FileId fileId) {
        return fileRepository.lockById(fileId.value()).map(fileMapper::mapFileToDomain);
    }

    @Override
    public List<File> findActiveAncestorDirectories(NamespaceId namespaceId, String path) {
        if (path == null || "/".equals(path)) {
            return List.of();
        }
        // "/a/b/c" → the folders ("/", "a"), ("/a", "b"), ("/a/b", "c"), root-most first.
        List<String> paths = new ArrayList<>();
        List<String> names = new ArrayList<>();
        String walked = "/";
        for (String name : path.substring(1).split("/")) {
            paths.add(walked);
            names.add(name);
            walked = "/".equals(walked) ? "/" + name : walked + "/" + name;
        }
        // The query matches any path with any name; keep only the exact pairs, in path order.
        Map<List<String>, FileJpaEntity> bySlot = fileRepository.findActiveDirectoriesAt(namespaceId.value(), paths, names)
                .stream()
                .collect(Collectors.toMap(f -> List.of(f.getPath(), f.getName()), f -> f, (a, b) -> a));
        List<File> ancestors = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            FileJpaEntity directory = bySlot.get(List.of(paths.get(i), names.get(i)));
            if (directory != null) {
                ancestors.add(fileMapper.mapFileToDomain(directory));
            }
        }
        return ancestors;
    }

    @Override
    public List<File> lockTrashedByNamespaceIdAndPathStartingWith(NamespaceId namespaceId, String pathPrefix) {
        return fileRepository
                .lockTrashedSubtreeByNamespaceIdAndPathPrefix(namespaceId.value(), pathPrefix, escapeLikePattern(pathPrefix))
                .stream()
                .map(fileMapper::mapFileToDomain)
                .toList();
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
    public List<File> findExpiredTrash(LocalDateTime cutoff) {
        return fileRepository
                .findByStatusAndTrashedAtBefore(FileStatus.TRASHED, cutoff)
                .stream()
                .map(fileMapper::mapFileToDomain)
                .collect(Collectors.toList());
    }

    @Override
    public FileVersion saveFileVersion(FileVersion fileVersion) {
        return saveFileVersions(List.of(fileVersion)).getFirst();
    }

    @Override
    public List<FileVersion> saveFileVersions(List<FileVersion> fileVersions) {
        List<FileVersionJpaEntity> entities = fileVersions.stream().map(fileVersion -> new FileVersionJpaEntity(
                fileVersion.getFileId(), fileVersion.getOwnerId(), fileVersion.getFileSize(),
                fileVersion.getUploadId(), fileVersion.getHashes()
        )).toList();
        return fileVersionRepository.saveAll(entities).stream().map(fileMapper::mapFileVersionToDomain).toList();
    }

    @Override
    public List<FileVersion> findAllByIds(Collection<UUID> versionIds) {
        return fileVersionRepository.findAllById(versionIds).stream()
                .map(fileMapper::mapFileVersionToDomain)
                .toList();
    }

    @Override
    public Optional<FileVersion> findByUploadId(UUID uploadId) {
        return fileVersionRepository.findByUploadId(uploadId).map(fileMapper::mapFileVersionToDomain);
    }

    @Override
    public List<FileVersion> findAllByUploadIds(Collection<UUID> uploadIds) {
        if (uploadIds.isEmpty()) {
            return List.of();
        }
        return fileVersionRepository.findByUploadIdIn(uploadIds).stream()
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

    @Override
    public List<UUID> findFileIdsWithVersionsToPrune(LocalDateTime cutoff, int keep, UUID afterFileId, int limit) {
        return fileVersionRepository.findFileIdsWithVersionsToPrune(cutoff, keep, afterFileId, limit);
    }

    @Override
    public List<FileVersion> findVersionsToPrune(FileId fileId, LocalDateTime cutoff, int keep) {
        return fileVersionRepository.findVersionsToPrune(fileId.value(), cutoff, keep).stream()
                .map(fileMapper::mapFileVersionToDomain)
                .toList();
    }

    @Override
    public void deleteFileVersions(List<FileVersion> versions) {
        // Entity deletes, not a bulk JPQL delete: the blocklist (file_version_block) goes with each row.
        fileVersionRepository.deleteAllById(versions.stream().map(FileVersion::getId).toList());
    }
}
