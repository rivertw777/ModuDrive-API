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

/**
 * Logins waiting for their emailed code as {@code login-challenge:{sha256(id)}} hashes (spec 004 2-1).
 * Wrong-code cap, request window and resend cooldown match member-service's sign-up email check.
 */
@Component
@RequiredArgsConstructor
class RedisLoginChallengeStore implements LoginChallengePort {

    private static final String KEY_PREFIX = "login-challenge:";
    private static final String REQUESTS_PREFIX = "login-code-requests:";
    private static final String COOLDOWN_PREFIX = "login-code-cooldown:";
    /** How long a login waits for its code — from the login, then from each code sent; the code lives as long. */
    static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_WRONG_CODES = 5;
    /** Codes one address can be sent per window — without it, anyone with the password can flood the mailbox. */
    static final int MAX_REQUESTS = 5;
    static final Duration REQUEST_WINDOW = Duration.ofMinutes(15);
    /** Gap between two codes for one address, so a double click or rapid resends don't each send a mail. */
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    private static final String MISMATCH = "mismatch";
    private static final String EXHAUSTED = "exhausted";

    private static final RedisScript<Long> CREATE_SCRIPT =
            RedisRepository.loadScript("scripts/create-login-challenge.lua", Long.class);
    private static final RedisScript<Long> ISSUE_CODE_SCRIPT =
            RedisRepository.loadScript("scripts/issue-login-challenge-code.lua", Long.class);
    private static final RedisScript<Long> COUNT_REQUEST_SCRIPT =
            RedisRepository.loadScript("scripts/count-login-code-request.lua", Long.class);
    private static final RedisScript<String> FIND_EMAIL_SCRIPT =
            RedisScript.of("return redis.call('HGET', KEYS[1], 'email')", String.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> CONFIRM_SCRIPT =
            RedisRepository.loadScript("scripts/confirm-login-challenge.lua", List.class);

    private final RedisRepository redisRepository;

    @Override
    public LoginChallengeId createChallenge(MemberAuthData memberAuthData, MemberEmail memberEmail) {
        LoginChallengeId challengeId = new LoginChallengeId(SecureTokens.newToken());
        redisRepository.executeScript(
                CREATE_SCRIPT,
                List.of(key(challengeId)),
                memberAuthData.getMemberId(),
                String.join(",", memberAuthData.getMemberRoles()),
                memberEmail.value(),
                String.valueOf(TTL.toMillis())
        );
        return challengeId;
    }

    @Override
    public MemberEmail findEmail(LoginChallengeId challengeId) {
        String email = redisRepository.executeScript(FIND_EMAIL_SCRIPT, List.of(key(challengeId)));
        if (email == null) {
            throw new BusinessException(AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);
        }
        return new MemberEmail(email);
    }

    @Override
    public CodeRequest requestCode(MemberEmail memberEmail) {
        Long requests = redisRepository.executeScript(COUNT_REQUEST_SCRIPT,
                List.of(REQUESTS_PREFIX + memberEmail.normalized(), cooldownKey(memberEmail)),
                String.valueOf(REQUEST_WINDOW.toMillis()), String.valueOf(RESEND_COOLDOWN.toMillis()));
        if (requests == null || requests == 0) {
            return CodeRequest.TOO_SOON;
        }
        return requests <= MAX_REQUESTS ? CodeRequest.ALLOWED : CodeRequest.TOO_MANY;
    }

    @Override
    public void issueCode(LoginChallengeId challengeId, String code) {
        Long issued = redisRepository.executeScript(ISSUE_CODE_SCRIPT, List.of(key(challengeId)),
                code, String.valueOf(TTL.toMillis()));
        if (issued == null || issued == 0) {
            throw new BusinessException(AuthExceptionCase.LOGIN_VERIFICATION_EXPIRED);
        }
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
        if (result.size() == 2 && EXHAUSTED.equals(result.get(0))) {
            // The code can't be used any more, so a new one can be asked for at once.
            redisRepository.delete(cooldownKey(new MemberEmail((String) result.get(1))));
            throw new BusinessException(AuthExceptionCase.LOGIN_VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        MemberEmail memberEmail = new MemberEmail((String) result.get(2));
        redisRepository.delete(cooldownKey(memberEmail));
        MemberAuthData memberAuthData = MemberAuthData.create(
                new MemberId((String) result.get(0)),
                new MemberRoles(parseRoles((String) result.get(1))));
        return new LoginChallenge(memberAuthData, memberEmail);
    }

    private static String key(LoginChallengeId challengeId) {
        return KEY_PREFIX + SecureTokens.sha256Hex(challengeId.value());
    }

    /** Keyed like the request count, so a differently-cased address can't dodge the cooldown. */
    private static String cooldownKey(MemberEmail memberEmail) {
        return COOLDOWN_PREFIX + memberEmail.normalized();
    }

    private static List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(",")).filter(role -> !role.isBlank()).toList();
    }
}
