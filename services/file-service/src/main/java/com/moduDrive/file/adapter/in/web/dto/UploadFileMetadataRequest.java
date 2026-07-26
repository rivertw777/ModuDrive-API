package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadFileMetadataRequest(
        @NotBlank String name,
        @NotBlank String path,
        @NotNull Boolean directory
) {}
