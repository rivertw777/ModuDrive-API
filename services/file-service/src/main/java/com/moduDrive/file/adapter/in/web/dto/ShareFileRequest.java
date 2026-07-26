package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.Permission;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ShareFileRequest(
        @NotNull UUID sharedWithUserId,
        @NotNull Permission permission
) {}
