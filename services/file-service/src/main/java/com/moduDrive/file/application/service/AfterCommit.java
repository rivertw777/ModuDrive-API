package com.moduDrive.file.application.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Defers an irreversible side effect (a cross-service call that can't be rolled back, like
 * purging S3 blocks) until the current transaction has actually committed — so a later rollback
 * in the same transaction (a sibling row failing, a constraint violation) can't leave that side
 * effect done while the DB state that justified it gets undone. Runs immediately if no
 * transaction is active, so a caller outside a transaction still works. */
final class AfterCommit {

    private AfterCommit() {}

    static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
