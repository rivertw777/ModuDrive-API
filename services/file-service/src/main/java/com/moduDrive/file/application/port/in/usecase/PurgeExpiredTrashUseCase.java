package com.moduDrive.file.application.port.in.usecase;

/** Driven by {@code TrashRetentionScheduler}, not a controller — sweeps every namespace's trash,
 * not just one caller's, so it takes no per-request input. */
public interface PurgeExpiredTrashUseCase {

    void purgeExpiredTrash();
}
