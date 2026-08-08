package com.moduDrive.file.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateFileFavoriteRequest(
        @NotNull Boolean favorite
) {}
