package com.moduDrive.gateway.security;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Successful session checks, kept for a few seconds so a page load's burst of API calls costs one
 * auth-service call, not one each. Concurrent checks of one session share a single call. A deleted
 * session can keep passing for up to {@link #TTL} on a gateway instance that cached it (spec 004 3).
 * Failures are never kept: a failed future leaves the cache.
 */
@Component
class SessionCheckCache {

    static final Duration TTL = Duration.ofSeconds(5);

    // Sessions checked within one TTL, per instance. Past it the oldest go first: more misses, never wrong.
    private final AsyncCache<String, Authentication> checks = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(10_000)
            .buildAsync();

    /** The cached result, or {@code check}'s, which is then kept. */
    Mono<Authentication> get(String sessionId, Mono<Authentication> check) {
        // The check runs once for every waiter, in the first one's context (its trace).
        return Mono.deferContextual(context -> join(
                checks.get(key(sessionId), (key, executor) -> check.contextWrite(context).toFuture())));
    }

    /** The cached result, or {@code check}'s, which isn't kept. */
    Mono<Authentication> getWithoutCaching(String sessionId, Mono<Authentication> check) {
        CompletableFuture<Authentication> cached = checks.getIfPresent(key(sessionId));
        return cached != null ? join(cached) : check;
    }

    void evict(String sessionId) {
        checks.synchronous().invalidate(key(sessionId));
    }

    // One waiter cancelling (a client gone) must not cancel the shared check for the others.
    private static Mono<Authentication> join(CompletableFuture<Authentication> future) {
        return Mono.fromFuture(future, true);
    }

    // Hashed like auth-service's Redis key: the raw session id isn't kept anywhere outside the cookie.
    private static String key(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
