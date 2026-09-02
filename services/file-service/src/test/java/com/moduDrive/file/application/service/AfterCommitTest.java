package com.moduDrive.file.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitTest {

    @Nested
    @DisplayName("트랜잭션이 활성화되어 있을 때")
    class WhenTransactionIsActive {

        @Test
        void deferRunsUntilAfterCommitFires() {
            TransactionSynchronizationManager.initSynchronization();
            try {
                List<String> ran = new ArrayList<>();

                AfterCommit.run(() -> ran.add("purged"));

                // Not yet — registered against the transaction, not executed inline.
                assertThat(ran).isEmpty();

                TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());

                assertThat(ran).containsExactly("purged");
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Nested
    @DisplayName("트랜잭션이 활성화되어 있지 않을 때")
    class WhenNoTransactionIsActive {

        @Test
        void runsImmediately() {
            List<String> ran = new ArrayList<>();

            AfterCommit.run(() -> ran.add("purged"));

            assertThat(ran).containsExactly("purged");
        }
    }
}
