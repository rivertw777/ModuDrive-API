package com.moduDrive.member.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member {

    private final UUID id;
    private final String name;
    private final String email;
    private final String password;
    private final List<Role> roles;
    private final boolean isValid;

    public static Member create(MemberName memberName,
                                MemberEmail memberEmail,
                                MemberPassword memberPassword,
                                MemberRoles memberRoles,
                                MemberIsValid memberIsValid) {
        return new Member(
                null,
                memberName.nameValue,
                memberEmail.emailValue,
                memberPassword.passwordValue,
                memberRoles.roleValues,
                memberIsValid.isValidValue
        );
    }

    public static Member withId(MemberId memberId,
                                MemberName memberName,
                                MemberEmail memberEmail,
                                MemberPassword memberPassword,
                                MemberRoles memberRoles,
                                MemberIsValid memberIsValid) {
        return new Member(
                memberId.idValue,
                memberName.nameValue,
                memberEmail.emailValue,
                memberPassword.passwordValue,
                memberRoles.roleValues,
                memberIsValid.isValidValue
        );
    }

    public record MemberId(UUID idValue) {
    }

    public record MemberName(String nameValue) {
    }

    public record MemberEmail(String emailValue) {
    }

    public record MemberPassword(String passwordValue) {
    }

    public record MemberRoles(List<Role> roleValues) {
    }

    public record MemberIsValid(boolean isValidValue) {
    }

}
