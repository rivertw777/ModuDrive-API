package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileShare;

import java.util.List;
import java.util.UUID;

public interface FindFileSharePort {

    boolean existsByFileIdAndSharedWithUserId(FileId fileId, UUID sharedWithUserId);

    List<FileShare> findBySharedWithUserId(UUID sharedWithUserId);
}
