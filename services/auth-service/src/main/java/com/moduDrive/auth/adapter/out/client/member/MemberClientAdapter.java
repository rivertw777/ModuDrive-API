package com.moduDrive.auth.adapter.out.client.member;

import com.moduDrive.auth.application.port.out.AuthenticateMemberPort;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberId;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberRoles;
import com.moduDrive.common.api.dto.member.AuthenticateMemberRequest;
import com.moduDrive.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MemberClientAdapter implements AuthenticateMemberPort {

    private final MemberClient memberClient;

    @Override
    public MemberAuthData authenticateMember(AuthenticateMemberRequest authenticateMemberRequest) {
        val response = memberClient.authenticateMember(authenticateMemberRequest).getData();

        if (!response.isValid()) {
            throw new BusinessException(AuthExceptionCase.MEMBER_NOT_VALID);
        }

        return MemberAuthData.create(
                new MemberId(response.id()),
                new MemberRoles(response.roles())
        );
    }

}
