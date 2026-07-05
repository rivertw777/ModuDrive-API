package com.moduDrive.member.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void memberRoleHasExpectedValue() {
        assertThat(Role.MEMBER.getValue()).isEqualTo("member");
    }

    @Test
    void adminRoleHasExpectedValue() {
        assertThat(Role.ADMIN.getValue()).isEqualTo("admin");
    }
}
