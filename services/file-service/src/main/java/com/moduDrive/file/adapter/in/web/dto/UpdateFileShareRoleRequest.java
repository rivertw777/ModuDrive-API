package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateFileShareRoleRequest(
        @NotNull Role role
) {}
