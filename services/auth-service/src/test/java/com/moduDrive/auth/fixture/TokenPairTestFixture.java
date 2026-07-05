package com.moduDrive.auth.fixture;

import com.moduDrive.auth.domain.model.TokenPair;
import com.moduDrive.auth.domain.model.TokenPair.AccessToken;
import com.moduDrive.auth.domain.model.TokenPair.RefreshToken;
import com.moduDrive.auth.domain.model.TokenPair.TokenGrantType;
import com.moduDrive.auth.domain.model.TokenPair.TokenIssuedAt;

import java.util.Date;

public class TokenPairTestFixture {

    public static final AccessToken DEFAULT_ACCESS_TOKEN = new AccessToken("access-token");
    public static final RefreshToken DEFAULT_REFRESH_TOKEN = new RefreshToken("refresh-token");
    public static final TokenGrantType DEFAULT_GRANT_TYPE = new TokenGrantType("Bearer");

    public static TokenPair aTokenPair() {
        return TokenPair.create(DEFAULT_ACCESS_TOKEN, DEFAULT_REFRESH_TOKEN, DEFAULT_GRANT_TYPE,
                new TokenIssuedAt(new Date()));
    }
}
