package com.moduDrive.file.application.port.out;

import com.moduDrive.file.application.port.in.usecase.DirectoryPage;
import com.moduDrive.file.domain.model.DirectorySort;
import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FindFilePort {

    Optional<File> findById(FileId fileId);

    List<File> findByNamespaceIdAndPath(NamespaceId namespaceId, String path);

    /** One keyset-scrolled page of a directory's direct children (TRASHED/DELETED excluded),
     * ordered directories-first then by {@code sort}. {@code cursor} is a token from a previous
     * page's {@link DirectoryPage#nextCursor()}, or null for the first page. */
    DirectoryPage findDirectoryPage(NamespaceId namespaceId, String path, DirectorySort sort, String cursor, int limit);

    /** The active (not trashed, not purged) row at this namespace/path/name, if any — the one an
     * upload of the same name would collide with. A trashed file at that name doesn't occupy the
     * slot (see {@code uk_file_namespace_path_active_name}), so it's deliberately excluded here. */
    Optional<File> findActiveByNamespaceIdAndPathAndName(NamespaceId namespaceId, String path, String name);

    /** {@link #findActiveByNamespaceIdAndPathAndName}, locking the row until the transaction ends —
     * a commit adding a version, so the file can't be trashed (and purged without that version's
     * blocks) halfway through. One trashed while this waited is no longer active and isn't returned. */
    Optional<File> lockActiveByNamespaceIdAndPathAndName(NamespaceId namespaceId, String path, String name);

    /** {@code findById}, locking the row — trashing it, so a commit adding a version to it finishes
     * first and the trash doesn't write back the version it read before. */
    Optional<File> lockById(FileId fileId);

    /** {@link #findByNamespaceIdAndPathStartingWith}, locking every row top-down — trashing a folder,
     * for the same reason as {@link #lockById}. */
    List<File> lockByNamespaceIdAndPathStartingWith(NamespaceId namespaceId, String pathPrefix);

    /** Entries whose {@code path} is {@code pathPrefix} or nested under it — i.e. every
     * descendant of the directory whose full path is {@code pathPrefix}. */
    List<File> findByNamespaceIdAndPathStartingWith(NamespaceId namespaceId, String pathPrefix);

    /** The trash view: status TRASHED. A purged tombstone (status DELETED) is excluded — its
     * contents are already gone. */
    List<File> findTrashedNotPurged(NamespaceId namespaceId);

    List<File> findByNamespaceIdAndNameContaining(NamespaceId namespaceId, String query);

    List<File> findByNamespaceId(NamespaceId namespaceId);

    long sumFileSizeByNamespaceId(NamespaceId namespaceId);

    /** Every not-yet-purged trashed file across every namespace that was trashed before
     * {@code cutoff}. Used by the retention sweep; unlike the rest of this port, deliberately
     * not scoped to one namespace. */
    List<File> findExpiredTrash(LocalDateTime cutoff);
}
