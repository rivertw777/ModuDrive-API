package com.moduDrive.storage.adapter.out.redis;

import io.lettuce.core.cluster.SlotHash;
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

/** Runs the Lua scripts against a real Redis — the upload count and the atomic claim live there, not
 * in Java. */
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
    @DisplayName("URL을 받은 블록을 찾을 때")
    class WhenFindingScheduled {

        private static final String HASH_A = "a".repeat(64);
        private static final String HASH_B = "b".repeat(64);
        private static final String HASH_C = "c".repeat(64);

        @Test
        @DisplayName("기준 시각 뒤에 등록된 그 소유자의 블록만 돌려준다 (여러 키에 걸쳐)")
        void findsTheOwnersBlocksScheduledSince() throws InterruptedException {
            store.scheduleSweep(owner, HASH_A);
            Thread.sleep(5);
            Instant since = Instant.now();
            Thread.sleep(5);
            store.scheduleSweep(owner, HASH_B);
            store.scheduleSweep(UUID.randomUUID(), HASH_C);

            assertThat(store.findScheduledSince(owner, List.of(HASH_A, HASH_B, HASH_C, HASH_B), since))
                    .containsExactly(HASH_B);
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
            store.scheduleSweep(owner, "a1");
            Instant afterFirst = Instant.now().plusMillis(1);

            assertThat(store.claimStale(Instant.now().minusSeconds(60), 10)).isEmpty();
            assertThat(store.claimStale(afterFirst, 10)).containsExactly(new UploadedBlock(owner, "a1"));
            assertThat(store.claimStale(afterFirst, 10)).isEmpty();
        }

        @Test
        @DisplayName("돌려놓은 블록은 다음에 다시 거두되, 그 사이 다시 올라온 블록은 새 시각을 지킨다")
        void releasedBlocksAreClaimedAgainUnlessUploadedSince() throws InterruptedException {
            store.scheduleSweep(owner, "a1");
            store.scheduleSweep(owner, "b2");
            store.scheduleSweep(owner, "a2");
            Instant cutoff = Instant.now().plusMillis(1);
            Thread.sleep(5);
            List<UploadedBlock> claimed = store.claimStale(cutoff, 10);
            store.scheduleSweep(owner, "a2"); // uploaded again while claimed

            store.release(claimed, cutoff.minusSeconds(60));

            assertThat(store.claimStale(cutoff, 10))
                    .containsExactlyInAnyOrder(new UploadedBlock(owner, "a1"), new UploadedBlock(owner, "b2"));
        }

        @Test
        @DisplayName("URL만 발급되고 올라오지 않은 블록도 거둔다")
        void claimsABlockScheduledButNeverUploaded() {
            store.scheduleSweep(owner, "a1");

            assertThat(store.claimStale(Instant.now().plusMillis(1), 10)).containsExactly(new UploadedBlock(owner, "a1"));
        }

        @Test
        @DisplayName("여러 키에 나뉜 블록을 한도만큼 거두고, 한도보다 적게 돌려주면 남은 게 없다")
        void claimsAcrossIndexKeysUpToTheLimit() {
            List.of("01", "12", "a3", "f4", "f5").forEach(hash -> store.scheduleSweep(owner, hash));
            Instant cutoff = Instant.now().plusMillis(1);

            List<UploadedBlock> first = store.claimStale(cutoff, 3);
            List<UploadedBlock> rest = store.claimStale(cutoff, 3);

            assertThat(first).hasSize(3);
            assertThat(rest).hasSize(2);
            assertThat(first).doesNotContainAnyElementsOf(rest);
            assertThat(store.claimStale(cutoff, 3)).isEmpty();
        }

        @Test
        @DisplayName("다시 올라온 블록은 시각이 갱신돼 거둬지지 않는다")
        void reUploadingMovesTheBlockOutOfReach() throws InterruptedException {
            store.scheduleSweep(owner, "a1");
            Instant cutoff = Instant.now().plusMillis(1);
            Thread.sleep(5);
            store.scheduleSweep(owner, "a1");

            assertThat(store.claimStale(cutoff, 10)).isEmpty();
        }
    }

    @Test
    @DisplayName("정리 대상 목록은 해시 첫 글자로 16개 키에 나뉘어, 여러 슬롯에 퍼진다")
    void theSweepIndexSpreadsOverSlots() {
        long slots = "0123456789abcdef".chars()
                .mapToObj(c -> SlotHash.getSlot(RedisUploadedBlockStore.indexKey((char) c + "0")))
                .distinct().count();

        assertThat(slots).isEqualTo(16);
    }
}
