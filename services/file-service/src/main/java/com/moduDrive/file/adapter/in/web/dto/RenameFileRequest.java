package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameFileRequest(
        @NotBlank String name
) {}
