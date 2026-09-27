package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.application.port.out.LoginChallengePort;
import com.moduDrive.auth.domain.model.LoginChallenge;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberId;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberRoles;
import com.moduDrive.auth.domain.vo.LoginChallengeId;
import com.moduDrive.auth.domain.vo.MemberEmail;
import com.moduDrive.auth.exception.AuthExceptionCase;
import com.moduDrive.common.core.exception.BusinessException;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Logins waiting for their emailed code as {@code login-challenge:{sha256(id)}} hashes (spec 004 2-1). */
@Component
@RequiredArgsConstructor
class RedisLoginChallengeStore implements LoginChallengePort {

    private static final String KEY_PREFIX = "login-challenge:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int MAX_WRONG_CODES = 5;
    private static final String MISMATCH = "mismatch";

    private static final RedisScript<Long> CREATE_SCRIPT =
            RedisRepository.loadScript("scripts/create-login-challenge.lua", Long.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CONFIRM_SCRIPT =
            RedisRepository.loadScript("scripts/confirm-login-challenge.lua", List.class);

    private final RedisRepository redisRepository;

    @Override
    public LoginChallengeId createChallenge(MemberAuthData memberAuthData, MemberEmail memberEmail, String code) {
        LoginChallengeId challengeId = new LoginChallengeId(SecureTokens.newToken());
        redisRepository.executeScript(
                CREATE_SCRIPT,
                List.of(key(challengeId)),
                memberAuthData.getMemberId(),
                String.join(",", memberAuthData.getMemberRoles()),
                memberEmail.value(),
                code,
                String.valueOf(TTL.toMillis())
        );
        return challengeId;
    }

    @Override
    public LoginChallenge confirmChallenge(LoginChallengeId challengeId, String code) {
        List<?> result = redisRepository.executeScript(
                CONFIRM_SCRIPT, List.of(key(challengeId)), code, String.valueOf(MAX_WRONG_CODES));
        // A nil reply arrives as null or as a one-element list holding null.
        if (result == null || result.isEmpty() || result.get(0) == null) {
            throw new BusinessException(AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);
        }
        if (result.size() == 1 && MISMATCH.equals(result.get(0))) {
            throw new BusinessException(AuthExceptionCase.INVALID_LOGIN_VERIFICATION_CODE);
        }
        MemberAuthData memberAuthData = MemberAuthData.create(
                new MemberId((String) result.get(0)),
                new MemberRoles(parseRoles((String) result.get(1))));
        return new LoginChallenge(memberAuthData, new MemberEmail((String) result.get(2)));
    }

    private static String key(LoginChallengeId challengeId) {
        return KEY_PREFIX + SecureTokens.sha256Hex(challengeId.value());
    }

    private static List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(",")).filter(role -> !role.isBlank()).toList();
    }
}
