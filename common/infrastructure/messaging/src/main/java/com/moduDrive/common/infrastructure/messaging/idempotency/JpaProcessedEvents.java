package com.moduDrive.common.infrastructure.messaging.idempotency;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.transaction.support.TransactionTemplate;

/** For a consumer with its own database. The record is written in the caller's transaction, so it
 * commits with the business change and disappears with it on a rollback — a failed message is
 * processed again, a succeeded one never is. */
@Slf4j
class JpaProcessedEvents implements ProcessedEvents {

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private ScheduledExecutorService purger;

    JpaProcessedEvents(EntityManager entityManager, TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    /** The insert in {@link #markProcessed} is the real guard — it runs in the caller's transaction
     * and {@code uk_processed_event} rejects the second one. This is the cheap pre-check that keeps a
     * redelivery of something long since handled from doing the work again. */
    @Override
    public boolean claim(String queue, String deduplicationId) {
        return entityManager.createQuery(
                        "select e.id from ProcessedEventJpaEntity e "
                                + "where e.queueName = :queue and e.deduplicationId = :id", Long.class)
                .setParameter("queue", queue)
                .setParameter("id", deduplicationId)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }

    /** Nothing to give back: the record is written in the caller's transaction, which rolls back with
     * the work that failed. */
    @Override
    public void release(String queue, String deduplicationId) {
    }

    @Override
    public void markProcessed(String queue, String deduplicationId) {
        entityManager.persist(new ProcessedEventJpaEntity(queue, deduplicationId));
    }

    void start() {
        purger = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("processed-event-purge").factory());
        purger.scheduleWithFixedDelay(this::purgeExpired, 1, 60, TimeUnit.MINUTES);
    }

    void stop() {
        purger.shutdownNow();
    }

    void purgeExpired() {
        try {
            int purged = transactionTemplate.execute(status -> entityManager
                    .createQuery("delete from ProcessedEventJpaEntity e where e.processedAt < :cutoff")
                    .setParameter("cutoff", Instant.now().minus(RETENTION))
                    .executeUpdate());
            if (purged > 0) {
                log.info("Purged {} processed-event records older than {}", purged, RETENTION);
            }
        } catch (Exception e) {
            log.error("Processed-event purge failed", e);
        }
    }
}
