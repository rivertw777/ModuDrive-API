package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindFilePort {

    Optional<File> findById(FileId fileId);

    Optional<File> findByLinkToken(UUID linkToken);

    List<File> findByNamespaceIdAndPath(NamespaceId namespaceId, String path);

    /** Entries whose {@code path} is {@code pathPrefix} or nested under it — i.e. every
     * descendant of the directory whose full path is {@code pathPrefix}. */
    List<File> findByNamespaceIdAndPathStartingWith(NamespaceId namespaceId, String pathPrefix);

    List<File> findByNamespaceIdAndStatus(NamespaceId namespaceId, FileStatus status);

    List<File> findByNamespaceIdAndFavorite(NamespaceId namespaceId);

    List<File> findByNamespaceIdAndNameContaining(NamespaceId namespaceId, String query);

    List<File> findByNamespaceId(NamespaceId namespaceId);

    long sumFileSizeByNamespaceId(NamespaceId namespaceId);
}
