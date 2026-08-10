package com.moduDrive.auth.adapter.in.web.dto;

import java.util.Date;

public record LoginResponse(
        String accessToken,
        String grantType,
        Date issuedAt
) {
}
