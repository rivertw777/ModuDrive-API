package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateDirectoryRequest(
        @NotNull Long userId,
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "^/[^.\\\\]*$", message = "path must start with / and must not contain . or \\") String path
) {}
