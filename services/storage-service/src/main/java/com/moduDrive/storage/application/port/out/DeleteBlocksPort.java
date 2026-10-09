package com.moduDrive.storage.application.port.out;

import java.time.Instant;

public interface DeleteBlocksPort {

    /** Deletes the block at {@code key} unless it was written after {@code decidedAt} — then it
     * belongs to an upload that started after the decision to delete, and is kept. A key that's
     * already gone is a no-op, so this is safe to repeat. */
    void deleteUnlessRewritten(String key, Instant decidedAt);
}
