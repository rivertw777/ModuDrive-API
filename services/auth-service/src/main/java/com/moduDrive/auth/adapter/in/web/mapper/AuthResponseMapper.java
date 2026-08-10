package com.moduDrive.auth.adapter.in.web.mapper;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.adapter.in.web.dto.LoginResponse;
import com.moduDrive.common.api.dto.auth.ValidateTokenResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthResponseMapper {

    public LoginResponse toLoginResponse(TokenPair tokenPair) {
        return new LoginResponse(
                tokenPair.getAccessToken(),
                tokenPair.getGrantType(),
                tokenPair.getIssuedAt()
        );
    }

    public ValidateTokenResponse toValidateTokenResponse(MemberAuthData memberAuthData) {
        return new ValidateTokenResponse(
                memberAuthData.getMemberId(),
                memberAuthData.getMemberRoles());
    }

}
