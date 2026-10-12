package com.moduDrive.common.event.file;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published by file-service (queue {@link FileQueues#BLOCKS_PURGE_REQUESTED}) in the transaction that
 * deletes the unreferenced {@code block} rows, so the hashes must travel in the event.
 * storage-service deletes {@code blocks/{ownerId}/{hash}} only if last written at or before
 * {@code decidedAt}; a later write belongs to a new upload. Deleting a missing object is a no-op, so
 * redeliveries need no idempotency check. */
public record BlocksPurgeRequested(UUID ownerId, List<String> hashes, Instant decidedAt) {
}
