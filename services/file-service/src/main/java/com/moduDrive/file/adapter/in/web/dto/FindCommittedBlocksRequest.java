package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record FindCommittedBlocksRequest(
        @NotNull UUID ownerId,
        @NotNull @Size(max = 1000) List<@NotNull String> hashes
) {}
