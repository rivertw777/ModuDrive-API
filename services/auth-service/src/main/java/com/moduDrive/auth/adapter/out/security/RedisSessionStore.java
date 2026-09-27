package com.moduDrive.auth.adapter.out.security;

import com.moduDrive.auth.application.port.out.CreateSessionPort;
import com.moduDrive.auth.application.port.out.DeleteSessionPort;
import com.moduDrive.auth.application.port.out.FindSessionPort;
import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberId;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberRoles;
import com.moduDrive.auth.domain.vo.SessionId;
import com.moduDrive.common.infrastructure.redis.RedisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Sessions live in Redis as {@code session:{sha256(id)}} hashes. Only the hash is stored, so a
 * leaked dump or backup can't be turned back into a working cookie. {@code member-sessions:{memberId}}
 * lists each member's session keys, so all of them can be ended at once.
 */
@Component
class RedisSessionStore implements CreateSessionPort, FindSessionPort, DeleteSessionPort {

    private static final String KEY_PREFIX = "session:";
    private static final String MEMBER_INDEX_PREFIX = "member-sessions:";

    private static final RedisScript<Long> CREATE_SCRIPT =
            RedisRepository.loadScript("scripts/create-session.lua", Long.class);
    private static final RedisScript<Long> DELETE_MEMBER_SESSIONS_SCRIPT =
            RedisRepository.loadScript("scripts/delete-member-sessions.lua", Long.class);
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> TOUCH_SCRIPT =
            RedisRepository.loadScript("scripts/touch-session.lua", List.class);

    private final RedisRepository redisRepository;
    private final String idleTimeoutMillis;
    private final String absoluteTimeoutMillis;

    RedisSessionStore(
            RedisRepository redisRepository,
            @Value("${session.idle-timeout}") Duration idleTimeout,
            @Value("${session.absolute-timeout}") Duration absoluteTimeout
    ) {
        this.redisRepository = redisRepository;
        this.idleTimeoutMillis = String.valueOf(idleTimeout.toMillis());
        this.absoluteTimeoutMillis = String.valueOf(absoluteTimeout.toMillis());
    }

    @Override
    public SessionId createSession(MemberAuthData memberAuthData) {
        SessionId sessionId = new SessionId(SecureTokens.newToken());
        redisRepository.executeScript(
                CREATE_SCRIPT,
                List.of(key(sessionId), MEMBER_INDEX_PREFIX + memberAuthData.getMemberId()),
                memberAuthData.getMemberId(),
                String.join(",", memberAuthData.getMemberRoles()),
                idleTimeoutMillis,
                absoluteTimeoutMillis
        );
        return sessionId;
    }

    @Override
    public Optional<MemberAuthData> findSession(SessionId sessionId, boolean touch) {
        List<?> result = redisRepository.executeScript(
                TOUCH_SCRIPT,
                List.of(key(sessionId)),
                idleTimeoutMillis,
                absoluteTimeoutMillis,
                touch ? "1" : "0"
        );
        if (result == null || result.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(MemberAuthData.create(
                new MemberId((String) result.get(0)),
                new MemberRoles(parseRoles((String) result.get(1)))
        ));
    }

    // ponytail: the member's index keeps a logged-out or expired session's key until the index
    // itself expires; deleting it again later is a no-op, so it isn't worth an SREM here.
    @Override
    public void deleteSession(SessionId sessionId) {
        redisRepository.delete(key(sessionId));
    }

    @Override
    public void deleteAllSessions(String memberId) {
        redisRepository.executeScript(DELETE_MEMBER_SESSIONS_SCRIPT, List.of(MEMBER_INDEX_PREFIX + memberId));
    }

    private static String key(SessionId sessionId) {
        return KEY_PREFIX + SecureTokens.sha256Hex(sessionId.value());
    }

    private static List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(",")).filter(role -> !role.isBlank()).toList();
    }
}
