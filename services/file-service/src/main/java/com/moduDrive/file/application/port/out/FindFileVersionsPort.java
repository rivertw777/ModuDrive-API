package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.File.FileId;
import com.moduDrive.file.domain.model.FileVersion;

import java.util.List;

public interface FindFileVersionsPort {

    List<FileVersion> findByFileIdOrderByCreatedAtDesc(FileId fileId, int limit);
}
