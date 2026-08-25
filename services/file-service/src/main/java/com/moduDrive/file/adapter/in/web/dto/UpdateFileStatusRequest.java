package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateFileStatusRequest(
        @NotNull @Positive Long fileSize,
        // ponytail: same 100_000 ceiling as storage-service's S3StorageAdapter — keep in sync,
        // both exist so a caller-supplied blockCount can't drive an oversized allocation.
        @NotNull @Positive @Max(100_000) Integer blockCount,
        @NotBlank String s3Path
) {}
