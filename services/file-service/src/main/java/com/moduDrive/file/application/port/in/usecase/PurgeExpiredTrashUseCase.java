package com.moduDrive.file.application.port.in.usecase;

/** Driven by {@code TrashRetentionScheduler}; sweeps every namespace's trash. */
public interface PurgeExpiredTrashUseCase {

    void purgeExpiredTrash();
}
