package com.moduDrive.member.adapter.out.security;

import com.moduDrive.member.domain.model.Member.MemberPassword;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpringPasswordEncoderTest {

    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private SpringPasswordEncoder springPasswordEncoder;

    @Nested
    @DisplayName("비밀번호를 인코딩할 때")
    class WhenEncodingPassword {

        @Test
        void delegatesToPasswordEncoderAndWrapsResult() {
            MemberPassword rawPassword = new MemberPassword("raw-password");
            given(passwordEncoder.encode("raw-password")).willReturn("encoded-password");

            MemberPassword result = springPasswordEncoder.encodePassword(rawPassword);

            assertThat(result.passwordValue()).isEqualTo("encoded-password");
        }
    }

    @Nested
    @DisplayName("비밀번호가 일치하는지 확인할 때")
    class WhenMatchingPassword {

        @Test
        void returnsTrueWhenMatching() {
            MemberPassword rawPassword = new MemberPassword("raw-password");
            MemberPassword encodedPassword = new MemberPassword("encoded-password");
            given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);

            boolean result = springPasswordEncoder.matchesPassword(rawPassword, encodedPassword);

            assertThat(result).isTrue();
        }

        @Test
        void returnsFalseWhenNotMatching() {
            MemberPassword rawPassword = new MemberPassword("raw-password");
            MemberPassword encodedPassword = new MemberPassword("encoded-password");
            given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(false);

            boolean result = springPasswordEncoder.matchesPassword(rawPassword, encodedPassword);

            assertThat(result).isFalse();
        }
    }
}
