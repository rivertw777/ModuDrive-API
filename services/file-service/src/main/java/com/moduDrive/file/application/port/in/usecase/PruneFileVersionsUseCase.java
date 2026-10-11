package com.moduDrive.file.application.port.in.usecase;

/** Driven by {@code FileVersionRetentionScheduler}, not a controller — sweeps every file's
 * versions, so it takes no per-request input. */
public interface PruneFileVersionsUseCase {

    void pruneFileVersions();
}
