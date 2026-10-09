package com.moduDrive.storage.adapter.out.redis;

import com.moduDrive.common.infrastructure.redis.RedisRepository;
import com.moduDrive.storage.application.port.out.ClaimStaleUploadsPort.UploadedBlock;
import com.moduDrive.storage.domain.model.Blocks;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the Lua scripts against a real Redis — the record/index pairing and the atomic claim live
 * there, not in Java. */
class RedisUploadedBlockStoreTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisUploadedBlockStore store;

    private final UUID owner = UUID.randomUUID();

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        store = new RedisUploadedBlockStore(new RedisRepository(redisTemplate));
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void flush() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("블록이 올라왔다고 기록할 때")
    class WhenRecording {

        @Test
        @DisplayName("크기를 24시간 동안 기억하고, 같은 소유자의 그 해시로만 찾힌다")
        void remembersTheSizeForTheOwnerOnly() {
            store.recordUploaded(owner, "h1", 4);

            assertThat(store.findUploaded(owner, List.of("h1", "h2"))).containsExactly(java.util.Map.entry("h1", 4));
            assertThat(store.findUploaded(UUID.randomUUID(), List.of("h1"))).isEmpty();
            long ttl = redisTemplate.getExpire("uploaded-block:" + owner + ":h1", TimeUnit.MILLISECONDS);
            assertThat(ttl).isBetween(Blocks.UPLOAD_TTL.toMillis() - 5_000, Blocks.UPLOAD_TTL.toMillis());
        }
    }

    @Nested
    @DisplayName("업로드 수를 셀 때")
    class WhenCounting {

        @Test
        @DisplayName("한도까지는 받고, 넘으면 거절하며 거절한 건 세지 않는다")
        void refusesPastTheLimitWithoutCountingTheRefusal() {
            assertThat(store.tryCountUpload(owner, 2)).isTrue();
            assertThat(store.tryCountUpload(owner, 2)).isTrue();
            assertThat(store.tryCountUpload(owner, 2)).isFalse();
            assertThat(store.tryCountUpload(UUID.randomUUID(), 2)).isTrue();

            assertThat(redisTemplate.opsForValue().get("upload-count:" + owner)).isEqualTo("2");
            long ttl = redisTemplate.getExpire("upload-count:" + owner, TimeUnit.MILLISECONDS);
            assertThat(ttl).isBetween(Blocks.UPLOAD_TTL.toMillis() - 5_000, Blocks.UPLOAD_TTL.toMillis());
        }
    }

    @Nested
    @DisplayName("오래된 업로드를 거둘 때")
    class WhenClaiming {

        @Test
        @DisplayName("기준 시각 전에 올라온 것만 한 번씩 돌려준다")
        void claimsEachStaleBlockOnce() {
            store.recordUploaded(owner, "h1", 4);
            Instant afterFirst = Instant.now().plusMillis(1);

            assertThat(store.claimStale(Instant.now().minusSeconds(60), 10)).isEmpty();
            assertThat(store.claimStale(afterFirst, 10)).containsExactly(new UploadedBlock(owner, "h1"));
            assertThat(store.claimStale(afterFirst, 10)).isEmpty();
        }

        @Test
        @DisplayName("다시 올라온 블록은 시각이 갱신돼 거둬지지 않는다")
        void reUploadingMovesTheBlockOutOfReach() throws InterruptedException {
            store.recordUploaded(owner, "h1", 4);
            Instant cutoff = Instant.now().plusMillis(1);
            Thread.sleep(5);
            store.recordUploaded(owner, "h1", 4);

            assertThat(store.claimStale(cutoff, 10)).isEmpty();
        }
    }
}
