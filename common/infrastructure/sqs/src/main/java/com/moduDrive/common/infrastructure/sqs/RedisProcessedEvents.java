package com.moduDrive.common.infrastructure.sqs;

import org.springframework.data.redis.core.StringRedisTemplate;

/** For a consumer without a database (mail-service). Nothing to join a transaction with — sending a
 * mail can't be rolled back — so the record is written after the work succeeds. Dying between the two
 * can still repeat that one message. Keys expire on their own after {@link #RETENTION}. */
class RedisProcessedEvents implements ProcessedEvents {

    private final StringRedisTemplate redisTemplate;

    RedisProcessedEvents(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isProcessed(String queue, String deduplicationId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(queue, deduplicationId)));
    }

    @Override
    public void markProcessed(String queue, String deduplicationId) {
        redisTemplate.opsForValue().set(key(queue, deduplicationId), "", RETENTION);
    }

    private static String key(String queue, String deduplicationId) {
        return "processed:" + queue + ":" + deduplicationId;
    }
}
