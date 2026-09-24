package com.moduDrive.storage.application.port.in.usecase;

import com.moduDrive.storage.application.port.in.command.PrepareArchiveCommand;

public interface PrepareArchiveUseCase {

    /** Checks access, size limits and quota up front, then returns a single-use token for
     * {@link OpenArchiveUseCase} — errors surface here, as JSON, not as a broken browser download. */
    String prepare(PrepareArchiveCommand command);
}
