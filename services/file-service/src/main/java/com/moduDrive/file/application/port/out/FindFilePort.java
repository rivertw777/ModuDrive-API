package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileStatus;
import com.moduDrive.file.domain.model.Namespace.NamespaceId;

import java.util.List;
import java.util.Optional;

public interface FindFilePort {

    Optional<File> findById(FileId fileId);

    List<File> findByNamespaceIdAndPath(NamespaceId namespaceId, String path);

    List<File> findByNamespaceIdAndStatus(NamespaceId namespaceId, FileStatus status);
}
