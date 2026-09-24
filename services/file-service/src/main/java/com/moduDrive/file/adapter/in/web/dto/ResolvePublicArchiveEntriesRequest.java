package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ResolvePublicArchiveEntriesRequest(String key, @NotEmpty List<@NotNull String> fileIds) {
}
