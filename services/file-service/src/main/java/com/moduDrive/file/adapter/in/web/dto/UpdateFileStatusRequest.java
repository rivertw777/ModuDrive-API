package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateFileStatusRequest(
        @NotNull @Positive Long fileSize,
        @NotNull @Positive Integer blockCount,
        @NotBlank String s3Path
) {}
