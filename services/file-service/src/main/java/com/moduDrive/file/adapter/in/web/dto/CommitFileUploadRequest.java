package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CommitFileUploadRequest(
        @NotNull UUID uploadId,
        // Zero is a real file (an empty .gitkeep in an uploaded folder), with an empty blocklist.
        @NotNull @PositiveOrZero Long size,
        // 1,280 = 5GB / 4MB, the most blocks a file can have; the service checks the exact count.
        @NotNull @Size(max = 1280) List<@NotNull @Pattern(regexp = "[0-9a-f]{64}") String> blocklist
) {}
