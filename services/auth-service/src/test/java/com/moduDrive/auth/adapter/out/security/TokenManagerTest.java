package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.domain.model.AccessTokenClaims;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.RefreshTokenClaims;
import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.auth.domain.model.TokenPair.RefreshToken;
import com.moduDrive.auth.domain.model.TokenPair.TokenFamilyId;
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
            assertThat(tokenPair.getFamilyId()).isNotBlank();
            assertThat(tokenPair.getJti()).isNotBlank();
        }

        @Test
        void createsDistinctFamilyPerLogin() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();

            TokenPair first = tokenManager.generateToken(memberAuthData);
            TokenPair second = tokenManager.generateToken(memberAuthData);

            assertThat(first.getFamilyId()).isNotEqualTo(second.getFamilyId());
            assertThat(first.getJti()).isNotEqualTo(second.getJti());
        }
    }

    @Nested
    @DisplayName("기존 패밀리로 토큰을 재발급할 때")
    class WhenGeneratingTokenWithExistingFamily {

        @Test
        void keepsFamilyIdAndMintsNewJti() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair original = tokenManager.generateToken(memberAuthData);

            TokenPair rotated = tokenManager.generateToken(
                    memberAuthData, new TokenFamilyId(original.getFamilyId()));

            assertThat(rotated.getFamilyId()).isEqualTo(original.getFamilyId());
            assertThat(rotated.getJti()).isNotEqualTo(original.getJti());
        }
    }

    @Nested
    @DisplayName("리프레시 토큰의 클레임을 읽을 때")
    class WhenReadingRefreshTokenClaims {

        @Test
        void returnsRoundTrippedFamilyIdAndJti() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthDataWithRoles(List.of("MEMBER", "ADMIN"));
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            RefreshTokenClaims claims = tokenManager.getRefreshTokenClaims(
                    new RefreshToken(tokenPair.getRefreshToken()));

            assertThat(claims.getFamilyId().getFamilyIdValue()).isEqualTo(tokenPair.getFamilyId());
            assertThat(claims.getJti().getJtiValue()).isEqualTo(tokenPair.getJti());
            assertThat(claims.getMemberAuthData().getMemberId()).isEqualTo("member-id");
            assertThat(claims.getMemberAuthData().getMemberRoles()).containsExactly("MEMBER", "ADMIN");
        }
    }

    @Nested
    @DisplayName("액세스 토큰으로 리프레시 클레임을 읽을 때")
    class WhenReadingRefreshClaimsFromAccessToken {

        @Test
        void throwsTokenInvalidException() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            Throwable thrown = catchThrowable(() -> tokenManager.getRefreshTokenClaims(
                    new RefreshToken(tokenPair.getAccessToken())));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.TOKEN_INVALID);
        }
    }

    @Nested
    @DisplayName("액세스 토큰의 클레임을 읽을 때")
    class WhenReadingAccessTokenClaims {

        @Test
        void returnsOriginalMemberAuthData() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthDataWithRoles(List.of("MEMBER", "ADMIN"));
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            AccessTokenClaims claims = tokenManager.getAccessTokenClaims(
                    new AccessToken(tokenPair.getAccessToken()));

            assertThat(claims.getMemberAuthData().getMemberId()).isEqualTo("member-id");
            assertThat(claims.getMemberAuthData().getMemberRoles()).containsExactly("MEMBER", "ADMIN");
        }

        @Test
        void returnsRoundTrippedJti() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            AccessTokenClaims claims = tokenManager.getAccessTokenClaims(
                    new AccessToken(tokenPair.getAccessToken()));

            assertThat(claims.getJti().getJtiValue()).isNotBlank();
        }

        @Test
        void returnsExpiryOneAccessTokenLifetimeAfterIssue() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            AccessTokenClaims claims = tokenManager.getAccessTokenClaims(
                    new AccessToken(tokenPair.getAccessToken()));

            // JWT exp has second precision, so compare against the truncated issuedAt + lifetime.
            long expectedEpochSecond = (tokenPair.getIssuedAt().getTime() + ONE_HOUR) / 1000L;
            assertThat(claims.getExpiresAt().getTime() / 1000L).isEqualTo(expectedEpochSecond);
        }

        @Test
        void returnsFamilyIdMatchingTheRefreshToken() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            AccessTokenClaims claims = tokenManager.getAccessTokenClaims(
                    new AccessToken(tokenPair.getAccessToken()));

            assertThat(claims.getFamilyId().getFamilyIdValue()).isEqualTo(tokenPair.getFamilyId());
        }

        @Test
        void keepsFamilyIdAcrossRotation() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair original = tokenManager.generateToken(memberAuthData);

            TokenPair rotated = tokenManager.generateToken(
                    memberAuthData, new TokenFamilyId(original.getFamilyId()));
            AccessTokenClaims claims = tokenManager.getAccessTokenClaims(
                    new AccessToken(rotated.getAccessToken()));

            assertThat(claims.getFamilyId().getFamilyIdValue()).isEqualTo(original.getFamilyId());
        }

        @Test
        void mintsDistinctAccessJtiFromRefreshJti() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            AccessTokenClaims claims = tokenManager.getAccessTokenClaims(
                    new AccessToken(tokenPair.getAccessToken()));

            assertThat(claims.getJti().getJtiValue()).isNotEqualTo(tokenPair.getJti());
        }
    }

    @Nested
    @DisplayName("리프레시 토큰으로 액세스 클레임을 읽을 때")
    class WhenReadingAccessClaimsFromRefreshToken {

        @Test
        void throwsTokenInvalidException() {
            TokenManager tokenManager = new TokenManager(secret, ONE_HOUR, ONE_HOUR);
            MemberAuthData memberAuthData = MemberAuthDataTestFixture.aMemberAuthData();
            TokenPair tokenPair = tokenManager.generateToken(memberAuthData);

            Throwable thrown = catchThrowable(() -> tokenManager.getAccessTokenClaims(
                    new AccessToken(tokenPair.getRefreshToken())));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.TOKEN_INVALID);
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

            Throwable thrown = catchThrowable(() -> tokenManager.getAccessTokenClaims(
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

            Throwable thrown = catchThrowable(() -> tokenManager.getAccessTokenClaims(
                    new AccessToken("not-a-valid-jwt")));

            assertThat(thrown)
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getExceptionCase())
                    .isEqualTo(AuthExceptionCase.TOKEN_INVALID);
        }
    }
}
