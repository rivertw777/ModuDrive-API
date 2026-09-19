package com.moduDrive.common.infrastructure.outbox;

/** PENDING → SENT once SQS accepts it (kept {@link OutboxRelay#SENT_RETENTION}, then purged), or
 * PENDING → FAILED when it can never be sent (kept for a human; set it back to PENDING to resend). */
enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
