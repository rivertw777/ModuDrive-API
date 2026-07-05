package com.moduDrive.auth.fixture;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberId;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberRoles;

import java.util.List;

public class MemberAuthDataTestFixture {

    public static final MemberId DEFAULT_MEMBER_ID = new MemberId("member-id");
    public static final MemberRoles DEFAULT_ROLES = new MemberRoles(List.of("MEMBER"));

    public static MemberAuthData aMemberAuthData() {
        return MemberAuthData.create(DEFAULT_MEMBER_ID, DEFAULT_ROLES);
    }

    public static MemberAuthData aMemberAuthDataWithRoles(List<String> roles) {
        return MemberAuthData.create(DEFAULT_MEMBER_ID, new MemberRoles(roles));
    }
}
