package com.moduDrive.member.fixture;

import com.moduDrive.member.domain.model.Member;
import com.moduDrive.member.domain.model.Member.MemberEmail;
import com.moduDrive.member.domain.model.Member.MemberId;
import com.moduDrive.member.domain.model.Member.MemberIsValid;
import com.moduDrive.member.domain.model.Member.MemberName;
import com.moduDrive.member.domain.model.Member.MemberPassword;
import com.moduDrive.member.domain.model.Member.MemberRoles;
import com.moduDrive.member.domain.model.Role;

import java.util.List;

public class MemberTestFixture {

    public static final MemberName DEFAULT_NAME = new MemberName("river");
    public static final MemberEmail DEFAULT_EMAIL = new MemberEmail("river@modudrive.com");
    public static final MemberPassword DEFAULT_PASSWORD = new MemberPassword("encoded-password");
    public static final MemberRoles DEFAULT_ROLES = new MemberRoles(List.of(Role.MEMBER));
    public static final MemberIsValid DEFAULT_IS_VALID = new MemberIsValid(true);

    public static Member aMember() {
        return Member.create(DEFAULT_NAME, DEFAULT_EMAIL, DEFAULT_PASSWORD, DEFAULT_ROLES, DEFAULT_IS_VALID);
    }

    public static Member aMemberWithId(MemberId memberId) {
        return Member.withId(memberId, DEFAULT_NAME, DEFAULT_EMAIL, DEFAULT_PASSWORD, DEFAULT_ROLES, DEFAULT_IS_VALID);
    }
}
