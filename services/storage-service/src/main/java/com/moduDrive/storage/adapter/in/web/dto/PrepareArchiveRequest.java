package com.moduDrive.storage.adapter.in.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PrepareArchiveRequest(@NotEmpty List<@NotNull UUID> fileIds) {
}
