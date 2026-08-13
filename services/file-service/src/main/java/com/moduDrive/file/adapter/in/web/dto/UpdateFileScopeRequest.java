package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.ShareScope;
import jakarta.validation.constraints.NotNull;

public record UpdateFileScopeRequest(
        @NotNull ShareScope scope
) {}
