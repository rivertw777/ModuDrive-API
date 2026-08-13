package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.File;
import com.moduDrive.file.domain.model.ShareScope;

import java.util.UUID;

public record FileScopeResponse(
        UUID fileId,
        ShareScope scope,
        UUID linkToken
) {
    public static FileScopeResponse from(File file) {
        return new FileScopeResponse(file.getId(), file.getAccessScope(), file.getLinkToken());
    }
}
