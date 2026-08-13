package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShareFileRequest(
        @NotBlank @Email String email,
        @NotNull Role role
) {}
