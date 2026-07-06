package com.moduDrive.auth.domain.model;

import com.moduDrive.auth.fixture.MemberAuthDataTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberAuthDataTest {

    @Test
    void createsMemberAuthDataFromGivenValues() {
        MemberAuthData memberAuthData = MemberAuthData.create(
                MemberAuthDataTestFixture.DEFAULT_MEMBER_ID,
                MemberAuthDataTestFixture.DEFAULT_ROLES);

        assertThat(memberAuthData.getMemberId()).isEqualTo("member-id");
        assertThat(memberAuthData.getMemberRoles()).containsExactly("MEMBER");
    }
}
