package com.moduDrive.common.api.dto.member;

import java.util.List;

public record AuthenticateMemberResponse(
        String id,
        boolean isValid,
        List<String> roles
) {
}
