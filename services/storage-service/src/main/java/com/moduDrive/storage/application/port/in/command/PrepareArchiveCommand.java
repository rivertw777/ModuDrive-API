package com.moduDrive.storage.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/** {@code userId} null means an anonymous request through a public link, authorized by the picked
 * ids themselves (LINK scope) or {@code key} (guest invite). */
@Getter
@EqualsAndHashCode(callSuper = false)
public class PrepareArchiveCommand extends SelfValidating<PrepareArchiveCommand> {

    private final UUID userId;

    private final String key;

    // ponytail: fixed cap on picked items (folders expand past it, bounded by the entry limit).
    @NotEmpty
    @Size(max = 1000)
    private final List<UUID> fileIds;

    public PrepareArchiveCommand(UUID userId, String key, List<UUID> fileIds) {
        this.userId = userId;
        this.key = key;
        this.fileIds = fileIds;
        this.validateSelf();
    }
}
