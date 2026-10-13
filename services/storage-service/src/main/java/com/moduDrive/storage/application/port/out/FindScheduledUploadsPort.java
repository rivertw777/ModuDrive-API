package com.moduDrive.storage.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface FindScheduledUploadsPort {

    /** The blocks among {@code hashes} whose upload URL was last signed after {@code since} — still on
     * the sweep's schedule, so the sweep can't take them before a commit does (spec 001 5). */
    Set<String> findScheduledSince(UUID ownerId, Collection<String> hashes, Instant since);
}
