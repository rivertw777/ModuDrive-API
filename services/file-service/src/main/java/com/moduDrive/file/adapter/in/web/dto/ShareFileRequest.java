package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.Permission;
import jakarta.validation.constraints.NotNull;

public record ShareFileRequest(
        @NotNull Long ownerId,
        @NotNull Long sharedWithUserId,
        @NotNull Permission permission
) {}
