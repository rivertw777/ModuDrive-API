package com.moduDrive.common.api.dto.auth;

import java.util.List;

public record ValidateSessionResponse(
        String memberId,
        List<String> memberRoles
) {
}
