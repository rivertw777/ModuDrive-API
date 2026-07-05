package com.moduDrive.auth.domain.model;

import com.moduDrive.auth.domain.model.TokenPair.TokenIssuedAt;
import com.moduDrive.auth.fixture.TokenPairTestFixture;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class TokenPairTest {

    @Test
    void createsTokenPairFromGivenValues() {
        Date issuedAt = new Date();

        TokenPair tokenPair = TokenPair.create(
                TokenPairTestFixture.DEFAULT_ACCESS_TOKEN,
                TokenPairTestFixture.DEFAULT_REFRESH_TOKEN,
                TokenPairTestFixture.DEFAULT_GRANT_TYPE,
                new TokenIssuedAt(issuedAt));

        assertThat(tokenPair.getAccessToken()).isEqualTo("access-token");
        assertThat(tokenPair.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(tokenPair.getGrantType()).isEqualTo("Bearer");
        assertThat(tokenPair.getIssuedAt()).isEqualTo(issuedAt);
    }
}
