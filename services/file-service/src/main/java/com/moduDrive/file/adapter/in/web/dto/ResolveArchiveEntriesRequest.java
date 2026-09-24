package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ResolveArchiveEntriesRequest(@NotNull UUID userId, @NotEmpty List<@NotNull UUID> fileIds) {
}
