package com.moduDrive.common.event.file;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Published by file-service (queue {@link FileQueues#BLOCKS_PURGE_REQUESTED}) in the transaction that
 * drops blocks nothing has referenced for a while — the same one that deletes their {@code block}
 * rows, so the hashes travel in the event: once it commits, file-service no longer knows them.
 * storage-service deletes {@code blocks/{ownerId}/{hash}} for each, but only an object last written
 * at or before {@code decidedAt}: one uploaded again after the decision belongs to a new upload.
 * Deleting an object that is already gone is a no-op, so a redelivery needs no idempotency check. */
public record BlocksPurgeRequested(UUID ownerId, List<String> hashes, Instant decidedAt) {
}
