package com.moduDrive.file.application.port.in.command;

import com.moduDrive.common.core.validation.SelfValidating;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class ResolveArchiveEntriesCommand extends SelfValidating<ResolveArchiveEntriesCommand> {

    @NotEmpty
    private final List<UUID> fileIds;

    @NotNull
    private final UUID callerId;

    public ResolveArchiveEntriesCommand(List<UUID> fileIds, UUID callerId) {
        this.fileIds = fileIds;
        this.callerId = callerId;
        this.validateSelf();
    }
}
