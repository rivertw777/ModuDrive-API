package com.moduDrive.file.application.port.out;

import com.moduDrive.file.domain.model.FileAccess;

import java.util.List;
import java.util.UUID;

public interface FindFileAccessPort {

    List<FileAccess> findByUserIdOrderByAccessedAtDesc(UUID userId, int limit);
}
