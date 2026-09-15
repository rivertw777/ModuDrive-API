package com.moduDrive.file.adapter.in.web.dto;

import com.moduDrive.file.domain.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShareFileRequest(
        @NotBlank @Email String email,
        @NotNull Role role,
        /** Optional note shown in the invite mail (Drive-style share message). Never persisted. */
        @Size(max = 1000) String message
) {}
