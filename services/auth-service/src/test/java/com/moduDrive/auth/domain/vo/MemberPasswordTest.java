package com.moduDrive.auth.domain.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberPasswordTest {

    @Test
    void holdsGivenValue() {
        MemberPassword memberPassword = new MemberPassword("raw-password");

        assertThat(memberPassword.value()).isEqualTo("raw-password");
    }
}
