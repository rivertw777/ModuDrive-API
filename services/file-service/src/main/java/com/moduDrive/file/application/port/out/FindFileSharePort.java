package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;
import com.moduDrive.file.domain.model.FileShare.FileShareId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindFileSharePort {

    boolean existsByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId);

    Optional<FileShare> findByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId);

    Optional<FileShare> findByShareId(FileShareId shareId);

    List<FileShare> findByFileId(FileId fileId);

    List<FileShare> findBySharedWithUserId(UUID sharedWithUserId);
}
