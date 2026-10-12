package com.moduDrive.common.infrastructure.messaging.outbox;

/** PENDING → SENT once the broker accepts it (purged after {@link OutboxRelay#SENT_RETENTION}), or
 * PENDING → FAILED when it can never be sent (set it back to PENDING to resend). */
enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
