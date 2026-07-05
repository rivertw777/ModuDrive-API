package com.moduDrive.auth.domain.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberEmailTest {

    @Test
    void holdsGivenValue() {
        MemberEmail memberEmail = new MemberEmail("river@modudrive.com");

        assertThat(memberEmail.value()).isEqualTo("river@modudrive.com");
    }
}
