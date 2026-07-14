package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;

public interface FindFileSharePort {

    boolean existsByFileIdAndSharedWithUserId(FileId fileId, Long sharedWithUserId);
}
