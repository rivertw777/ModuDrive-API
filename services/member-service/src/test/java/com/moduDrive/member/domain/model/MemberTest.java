package com.moduDrive.member.domain.model;

import com.moduDrive.member.domain.model.Member.*;
import com.moduDrive.member.fixture.MemberTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    private final MemberName memberName = MemberTestFixture.DEFAULT_NAME;
    private final MemberEmail memberEmail = MemberTestFixture.DEFAULT_EMAIL;
    private final MemberPassword memberPassword = MemberTestFixture.DEFAULT_PASSWORD;
    private final MemberRoles memberRoles = MemberTestFixture.DEFAULT_ROLES;
    private final MemberIsValid memberIsValid = MemberTestFixture.DEFAULT_IS_VALID;

    @Nested
    @DisplayName("id 없이 회원을 생성할 때")
    class WhenCreatingWithoutId {

        @Test
        void createsMemberWithNullId() {
            Member member = Member.create(memberName, memberEmail, memberPassword, memberRoles, memberIsValid);

            assertThat(member.getId()).isNull();
            assertThat(member.getName()).isEqualTo(memberName.nameValue());
            assertThat(member.getEmail()).isEqualTo(memberEmail.emailValue());
            assertThat(member.getPassword()).isEqualTo(memberPassword.passwordValue());
            assertThat(member.getRoles()).isEqualTo(memberRoles.roleValues());
            assertThat(member.isValid()).isEqualTo(memberIsValid.isValidValue());
        }
    }

    @Nested
    @DisplayName("id를 포함해 회원을 생성할 때")
    class WhenCreatingWithId {

        @Test
        void createsMemberWithGivenId() {
            MemberId memberId = new MemberId(UUID.randomUUID());

            Member member = Member.withId(memberId, memberName, memberEmail, memberPassword, memberRoles, memberIsValid);

            assertThat(member.getId()).isEqualTo(memberId.idValue());
            assertThat(member.getName()).isEqualTo(memberName.nameValue());
            assertThat(member.getEmail()).isEqualTo(memberEmail.emailValue());
            assertThat(member.getPassword()).isEqualTo(memberPassword.passwordValue());
            assertThat(member.getRoles()).isEqualTo(memberRoles.roleValues());
            assertThat(member.isValid()).isEqualTo(memberIsValid.isValidValue());
        }
    }
}
