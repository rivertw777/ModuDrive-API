package com.moduDrive.file.application.port.in.usecase;

/** Driven by {@code FileVersionRetentionScheduler}; sweeps every file's versions. */
public interface PruneFileVersionsUseCase {

    void pruneFileVersions();
}
