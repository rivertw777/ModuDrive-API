package com.moduDrive.file.application.port.out;

public interface ExtendStatementTimeoutPort {

    /** Spec 006 2-5-1: lifts the 5s statement limit for the rest of the current transaction only. For a
     * folder cascade, whose statements grow with the subtree — a 100k-file purge's block release runs past
     * 5s while commits share the DB. Call it inside the transaction, before the long statements. */
    void extendStatementTimeout();
}
