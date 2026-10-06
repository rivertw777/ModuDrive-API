package com.moduDrive.common.infrastructure.messaging.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/** For a consumer without a database (mail-service). Nothing to join a transaction with — sending a
 * mail can't be rolled back — so the claim is taken before the send and given back if it fails.
 * {@code SETNX} makes that one atomic step: two copies of the same message arriving together can't
 * both find the key missing, which is what a plain read-then-write allowed. */
class RedisProcessedEvents implements ProcessedEvents {

    /** How long a claim survives without being confirmed or given back — a process that dies mid-send
     * leaves one. Kept at the queues' visibility timeout so it lapses about when SQS hands the message
     * to someone else: a second mail is better than none. */
    // ponytail: hardcoded to match .docker/localstack/init-aws.sh; make it a property if a queue
    // ever needs a different visibility timeout.
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(10);

    /** A claim's value, distinct from a processed record's, so giving a claim back can't erase a record
     * written in the meantime (SES's Send event for a send that only looked failed). */
    private static final String CLAIMED = "claimed";
    private static final String PROCESSED = "";
    private static final RedisScript<Long> DELETE_IF_CLAIMED = RedisScript.of(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) end return 0", Long.class);

    private final StringRedisTemplate redisTemplate;

    RedisProcessedEvents(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean claim(String queue, String deduplicationId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key(queue, deduplicationId), CLAIMED, CLAIM_LEASE));
    }

    @Override
    public void release(String queue, String deduplicationId) {
        redisTemplate.execute(DELETE_IF_CLAIMED, List.of(key(queue, deduplicationId)), CLAIMED);
    }

    /** Turns the short claim into the full record: the mail is out, so this key has to outlive every
     * resend the producer's outbox can still make. */
    @Override
    public void markProcessed(String queue, String deduplicationId) {
        redisTemplate.opsForValue().set(key(queue, deduplicationId), PROCESSED, RETENTION);
    }

    private static String key(String queue, String deduplicationId) {
        return "processed:" + queue + ":" + deduplicationId;
    }
}
