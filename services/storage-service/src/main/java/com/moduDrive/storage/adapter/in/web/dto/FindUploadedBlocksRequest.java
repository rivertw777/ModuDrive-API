package com.moduDrive.storage.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record FindUploadedBlocksRequest(
        @NotNull UUID ownerId,
        // 1,280 = 5GB / 4MB, the most blocks a commit can name.
        @NotNull @Size(max = 1280) List<@NotNull String> hashes
) {}
