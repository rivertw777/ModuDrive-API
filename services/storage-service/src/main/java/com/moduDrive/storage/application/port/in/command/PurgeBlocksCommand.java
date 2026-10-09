package com.moduDrive.storage.application.port.in.command;

import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
public class PurgeBlocksCommand {

    private final UUID ownerId;
    private final List<String> hashes;
    private final Instant decidedAt;

    public PurgeBlocksCommand(UUID ownerId, List<String> hashes, Instant decidedAt) {
        this.ownerId = ownerId;
        this.hashes = List.copyOf(hashes);
        this.decidedAt = decidedAt;
    }
}
