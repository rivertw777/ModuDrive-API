package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDirectoryRequest(
        @NotNull Long userId,
        @NotBlank String name,
        @NotBlank String path
) {}
