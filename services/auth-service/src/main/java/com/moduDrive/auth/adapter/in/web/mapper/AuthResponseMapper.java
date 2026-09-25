package com.moduDrive.auth.adapter.in.web.mapper;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.common.api.dto.auth.ValidateSessionResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthResponseMapper {

    public ValidateSessionResponse toValidateSessionResponse(MemberAuthData memberAuthData) {
        return new ValidateSessionResponse(
                memberAuthData.getMemberId(),
                memberAuthData.getMemberRoles());
    }

}
