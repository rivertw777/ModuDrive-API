package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.auth.fixture.MemberAuthDataTestFixture;
import com.moduDrive.common.core.exception.BusinessException;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class TokenManagerTest {

    private static final long ONE_HOUR = 60 * 60 * 1000L;
    private String secret;

    @BeforeEach
    void setUp() {
        secret = Encoders.BASE64.encode(Keys.secretKeyFor(SignatureAlgorithm.HS256).getEncoded());
    }

    @Nested
    @DisplayName("토큰을 생성할 때")
    class WhenGeneratingToken {

        @Test
        void createsTokenPairWithBearerGrantType() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthDataWithRoles(List.of("MEMBER", "ADMIN"));

            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            assertThat(tokenPair.getAccessToken()).isNotBlank();
            assertThat(tokenPair.getRefreshToken()).isNotBlank();
            assertThat(tokenPair.getGrantType()).isEqualTo("Bearer");
        }
    }

    @Nested
    @DisplayName("유효한 토큰을 검증할 때")
    class WhenValidatingValidToken {

        @Test
        void returnsOriginalMemberAuthData() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthDataWithRoles(List.of("MEMBER", "ADMIN"));
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            MemberAuthData result = tokenManager.getMemberAuthDataFromToken(
                    new AccessToken(tokenPair.getAccessToken()));

            assertThat(result.getMemberId()).isEqualTo("member-id");
            assertThat(result.getMemberRoles()).containsExactly("MEMBER", "ADMIN");
        }
    }

    @Nested
    @DisplayName("만료된 토큰을 검증할 때")
    class WhenValidatingExpiredToken {

        @Test
        void throwsTokenExpiredException() {
            TokenManager tokenManager = new TokenManager(secret, -ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            Throwable thrown = catchThrowable(() -> tokenManager.getMemberAuthDataFromToken(
                    new AccessToken(tokenPair.getAccessToken())));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.TOKEN_EXPIRED);
        }
    }

    @Nested
    @DisplayName("형식이 올바르지 않은 토큰을 검증할 때")
    class WhenValidatingMalformedToken {

        @Test
        void throwsTokenInvalidException() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);

            Throwable thrown = catchThrowable(() -> tokenManager.getMemberAuthDataFromToken(
                    new AccessToken("not-a-valid-jwt")));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.TOKEN_INVALID);
        }
    }
}
