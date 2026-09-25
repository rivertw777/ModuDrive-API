package com.moduDrive.auth.adapter.in.web.controller;

import com.moduDrive.auth.adapter.in.web.dto.SessionResponse;
import com.moduDrive.common.core.annotation.WebAdapter;
import com.moduDrive.common.core.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the WEB ask "am I logged in?" on startup. The gateway only lets this through with a valid
 * session and has already resolved it into X_USER_ID, so there is nothing left to look up.
 */
@WebAdapter
@RestController
class GetSessionController {

    @GetMapping("/api/v1/auth/session")
    public ApiResponse<SessionResponse> getSession(@RequestHeader("X_USER_ID") String memberId) {
        return ApiResponse.success(new SessionResponse(memberId));
    }

}
