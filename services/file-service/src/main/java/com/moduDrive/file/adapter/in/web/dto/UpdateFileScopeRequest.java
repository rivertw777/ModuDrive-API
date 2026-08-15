package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.Role;
import com.moduDrive.file.domain.model.ShareScope;
import jakarta.validation.constraints.NotNull;

/** {@code role} is only read when {@code scope == LINK}; the service rejects a LINK request that
 * omits it. */
public record UpdateFileScopeRequest(
        @NotNull ShareScope scope,
        Role role
) {}
