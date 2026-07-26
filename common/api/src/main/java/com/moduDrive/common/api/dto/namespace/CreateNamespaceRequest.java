package com.moduDrive.common.api.dto.namespace;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateNamespaceRequest(
        @NotNull UUID userId
) {
}
