package com.moduDrive.member.adapter.in.web.mapper;

import com.moduDrive.common.api.dto.member.AuthenticateMemberResponse;
import com.moduDrive.common.api.dto.member.MemberResponse;
import com.moduDrive.member.adapter.in.web.dto.FindMemberResponse;
import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Role;
import org.springframework.stereotype.Component;

@Component
public class MemberResponseMapper {

    public AuthenticateMemberResponse toAuthenticateMemberResponse(Member member) {
        return new AuthenticateMemberResponse(
                member.getId().toString(),
                member.getName(),
                member.getEmail(),
                member.isValid(),
                member.getRoles().stream()
                        .map(Role::name)
                        .toList()
        );
    }

    public MemberResponse toMemberResponse(Member member) {
        return new MemberResponse(
                member.getId().toString(),
                member.getName(),
                member.getEmail()
        );
    }

    public FindMemberResponse toFindMemberResponse(Member member) {
        return new FindMemberResponse(
                member.getId().toString(),
                member.getName(),
                member.getEmail(),
                member.isValid()
        );
    }

}
